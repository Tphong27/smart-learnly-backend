package com.smartlearnly.backend.flashcard.staging.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardDocumentTextExtractionService.DocumentImage;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardDocumentTextExtractionService.DocumentTextExtractionResult;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultFlashcardDocumentTextExtractionService implements FlashcardDocumentTextExtractionService {
    private static final String SOURCE_TYPE_DOCX = "DOCX";
    private static final String SOURCE_TYPE_PDF = "PDF";
    private static final String IMAGE_TYPE_PNG = "image/png";
    private static final String IMAGE_TYPE_JPEG = "image/jpeg";
    private static final int MIN_TEXT_LENGTH_BEFORE_SKIPPING_IMAGES = 100;
    private static final int MAX_PDF_IMAGE_RECURSION_DEPTH = 4;
    private static final Set<String> SUPPORTED_EMBEDDED_IMAGE_TYPES = Set.of(
            IMAGE_TYPE_PNG,
            IMAGE_TYPE_JPEG,
            "image/webp"
    );
    private final FlashcardDocumentGenerationProperties properties;

    @Override
    public DocumentTextExtractionResult extract(MultipartFile file) {
        long startedAtNanos = System.nanoTime();
        String fileName = sanitizeOriginalFileName(file.getOriginalFilename());
        String extension = extractExtension(fileName);
        byte[] content = readBytes(file);
        ExtractionContent extraction = switch (extension) {
            case "pdf" -> extractPdfContent(content);
            case "docx" -> extractDocxContent(content);
            default -> throw new BusinessException(ErrorCode.INVALID_REQUEST, "Unsupported flashcard source file type");
        };
        String sourceType = extension.toUpperCase(Locale.ROOT);
        String text = normalizeExtractedText(extraction.text());
        DocumentTextExtractionResult result = new DocumentTextExtractionResult(
                sourceType,
                fileName,
                text,
                extraction.images(),
                extraction.renderedPageImages()
        );
        log.info(
                "Flashcard document extraction completed: sourceType={} selectedTextLength={} embeddedImagesConsidered={} pagesRendered={} elapsedMs={}",
                sourceType,
                text.length(),
                result.images().size(),
                result.renderedPageImages().size(),
                elapsedMillis(startedAtNanos)
        );
        return result;
    }

    private ExtractionContent extractPdfContent(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            List<String> pages = new ArrayList<>();
            for (int page = 1; page <= document.getNumberOfPages(); page += 1) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = normalizeBlock(stripper.getText(document));
                if (!text.isBlank()) {
                    pages.add(text);
                }
            }
            String text = String.join("\n\n", pages);
            if (!shouldExtractImages(text)) {
                return new ExtractionContent(text, List.of(), List.of());
            }
            return new ExtractionContent(
                    text,
                    extractPdfImages(document),
                    renderPdfPageImages(document)
            );
        }
        catch (IOException | RuntimeException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "PDF text could not be extracted");
        }
    }

    private ExtractionContent extractDocxContent(byte[] content) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            List<String> blocks = new ArrayList<>();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                addBlock(blocks, paragraph.getText());
            }
            for (XWPFTable table : document.getTables()) {
                addTableBlocks(blocks, table);
            }
            String text = String.join("\n\n", blocks);
            return new ExtractionContent(
                    text,
                    shouldExtractImages(text) ? extractDocxImages(document) : List.of(),
                    List.of()
            );
        }
        catch (IOException | RuntimeException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "DOCX text could not be extracted");
        }
    }

    private List<DocumentImage> extractDocxImages(XWPFDocument document) {
        List<DocumentImage> images = new ArrayList<>();
        for (XWPFPictureData picture : document.getAllPictures()) {
            if (images.size() >= maxEmbeddedImages()) {
                break;
            }
            byte[] data = picture.getData();
            if (data == null || data.length == 0 || data.length > maxEmbeddedImageBytes()) {
                continue;
            }
            String contentType = normalizeImageContentType(picture.getPackagePart().getContentType());
            if (!SUPPORTED_EMBEDDED_IMAGE_TYPES.contains(contentType)) {
                continue;
            }
            images.add(new DocumentImage(
                    sanitizeEmbeddedImageName(picture.getFileName(), "docx-image-" + (images.size() + 1)),
                    contentType,
                    data
            ));
        }
        return List.copyOf(images);
    }

    private List<DocumentImage> extractPdfImages(PDDocument document) {
        List<DocumentImage> images = new ArrayList<>();
        for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex += 1) {
            if (images.size() >= maxEmbeddedImages()) {
                break;
            }
            try {
                extractPdfImagesFromResources(
                        document.getPage(pageIndex).getResources(),
                        "pdf-page-" + (pageIndex + 1),
                        images,
                        0
                );
            }
            catch (IOException | RuntimeException ignored) {
                // Embedded PDF image extraction is best-effort; selectable text is still useful.
            }
        }
        return List.copyOf(images);
    }

    private void extractPdfImagesFromResources(
            PDResources resources,
            String prefix,
            List<DocumentImage> images,
            int depth
    ) throws IOException {
        if (resources == null || images.size() >= maxEmbeddedImages() || depth > MAX_PDF_IMAGE_RECURSION_DEPTH) {
            return;
        }
        for (COSName xObjectName : resources.getXObjectNames()) {
            if (images.size() >= maxEmbeddedImages()) {
                return;
            }
            try {
                PDXObject xObject = resources.getXObject(xObjectName);
                if (xObject instanceof PDImageXObject image) {
                    addPdfImage(images, image, prefix + "-" + xObjectName.getName() + ".png");
                } else if (xObject instanceof PDFormXObject form) {
                    extractPdfImagesFromResources(
                            form.getResources(),
                            prefix + "-" + xObjectName.getName(),
                            images,
                            depth + 1
                    );
                }
            }
            catch (IOException | RuntimeException ignored) {
                // Continue with other embedded images if one object cannot be decoded.
            }
        }
    }

    private void addPdfImage(List<DocumentImage> images, PDImageXObject image, String fileName) throws IOException {
        BufferedImage bufferedImage = image.getImage();
        if (bufferedImage == null) {
            return;
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(bufferedImage, "png", output)) {
            return;
        }
        byte[] data = output.toByteArray();
        if (data.length == 0 || data.length > maxEmbeddedImageBytes()) {
            return;
        }
        images.add(new DocumentImage(
                sanitizeEmbeddedImageName(fileName, "pdf-image-" + (images.size() + 1) + ".png"),
                IMAGE_TYPE_PNG,
                data
        ));
    }

    private List<DocumentImage> renderPdfPageImages(PDDocument document) {
        int maxPages = maxRenderedPdfPages();
        if (maxPages <= 0 || document.getNumberOfPages() <= 0) {
            return List.of();
        }
        PDFRenderer renderer = new PDFRenderer(document);
        List<DocumentImage> pageImages = new ArrayList<>();
        int pageLimit = Math.min(maxPages, document.getNumberOfPages());
        for (int pageIndex = 0; pageIndex < pageLimit; pageIndex += 1) {
            try {
                BufferedImage pageImage = renderer.renderImageWithDPI(pageIndex, pdfRenderDpi(), ImageType.RGB);
                byte[] content = encodeRenderedPageImage(pageImage);
                if (content.length == 0) {
                    continue;
                }
                pageImages.add(new DocumentImage(
                        "pdf-rendered-page-" + (pageIndex + 1) + ".jpg",
                        IMAGE_TYPE_JPEG,
                        content
                ));
            }
            catch (IOException | RuntimeException exception) {
                log.warn(
                        "Skipping rendered PDF page image for flashcard document extraction: page={} reason={}",
                        pageIndex + 1,
                        exception.getMessage()
                );
            }
        }
        return List.copyOf(pageImages);
    }

    private byte[] encodeRenderedPageImage(BufferedImage image) throws IOException {
        if (image == null) {
            return new byte[0];
        }
        BufferedImage current = toRgbImage(image);
        for (int attempt = 0; attempt < 3; attempt += 1) {
            byte[] content = encodeJpeg(current, renderedPageJpegQuality());
            if (content.length <= maxRenderedPageImageBytes()) {
                return content;
            }
            int width = Math.max(1, Math.round(current.getWidth() * 0.75F));
            int height = Math.max(1, Math.round(current.getHeight() * 0.75F));
            current = resizeImage(current, width, height);
        }
        byte[] content = encodeJpeg(current, Math.min(renderedPageJpegQuality(), 0.65F));
        return content.length <= maxRenderedPageImageBytes() ? content : new byte[0];
    }

    private byte[] encodeJpeg(BufferedImage image, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", output);
            return output.toByteArray();
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(quality);
            }
            writer.setOutput(imageOutput);
            writer.write(null, new IIOImage(image, null, null), params);
            return output.toByteArray();
        }
        finally {
            writer.dispose();
        }
    }

    private BufferedImage toRgbImage(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            graphics.drawImage(source, 0, 0, null);
            return rgb;
        }
        finally {
            graphics.dispose();
        }
    }

    private BufferedImage resizeImage(BufferedImage source, int width, int height) {
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, width, height, null);
            return resized;
        }
        finally {
            graphics.dispose();
        }
    }

    private void addTableBlocks(List<String> blocks, XWPFTable table) {
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                String text = normalizeBlock(cell.getText());
                if (!text.isBlank()) {
                    cells.add(text);
                }
                for (XWPFTable nestedTable : cell.getTables()) {
                    addTableBlocks(blocks, nestedTable);
                }
            }
            if (!cells.isEmpty()) {
                blocks.add(String.join(" | ", cells));
            }
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        }
        catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Uploaded file could not be read");
        }
    }

    private String sanitizeOriginalFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Uploaded file name is required");
        }
        String normalized = originalFileName.trim().replace('\\', '/');
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (fileName.isBlank() || fileName.contains("..")) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Uploaded file name is invalid");
        }
        return fileName;
    }

    private String extractExtension(String fileName) {
        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex < 0 || extensionIndex == fileName.length() - 1) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Uploaded file must be a DOCX or PDF file");
        }
        return fileName.substring(extensionIndex + 1).toLowerCase(Locale.ROOT);
    }

    private void addBlock(List<String> blocks, String value) {
        String normalized = normalizeBlock(value);
        if (!normalized.isBlank()) {
            blocks.add(normalized);
        }
    }

    private String normalizeExtractedText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        String[] paragraphs = normalized.split("\\n\\s*\\n");
        List<String> blocks = new ArrayList<>();
        for (String paragraph : paragraphs) {
            addBlock(blocks, paragraph);
        }
        return String.join("\n\n", blocks);
    }

    private String normalizeBlock(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00a0', ' ')
                .replaceAll("[\\t\\x0B\\f ]+", " ")
                .replaceAll("\\n\\s*\\n+", "\n\n")
                .replaceAll(" *\\n *", "\n")
                .trim();
    }

    private boolean shouldExtractImages(String text) {
        return normalizeExtractedText(text).length() < MIN_TEXT_LENGTH_BEFORE_SKIPPING_IMAGES;
    }

    private int maxEmbeddedImages() {
        return Math.max(0, properties.getMaxEmbeddedImages());
    }

    private long maxEmbeddedImageBytes() {
        return Math.max(1L, properties.getMaxEmbeddedImageSize().toBytes());
    }

    private int maxRenderedPdfPages() {
        return Math.max(0, properties.getMaxRenderedPdfPages());
    }

    private float pdfRenderDpi() {
        return Math.max(72F, properties.getPdfRenderDpi());
    }

    private long maxRenderedPageImageBytes() {
        return Math.max(1L, properties.getMaxRenderedPageImageSize().toBytes());
    }

    private float renderedPageJpegQuality() {
        return Math.max(0.1F, Math.min(1F, properties.getRenderedPageJpegQuality()));
    }

    private long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }

    private String normalizeImageContentType(String contentType) {
        String normalized = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
        if ("image/jpg".equals(normalized) || "image/pjpeg".equals(normalized)) {
            return "image/jpeg";
        }
        return normalized;
    }

    private String sanitizeEmbeddedImageName(String fileName, String fallback) {
        String value = fileName == null || fileName.isBlank() ? fallback : fileName.trim().replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).trim();
        if (value.isBlank() || value.contains("..")) {
            return fallback;
        }
        return value;
    }

    private record ExtractionContent(String text, List<DocumentImage> images, List<DocumentImage> renderedPageImages) {
        private ExtractionContent {
            images = images == null ? List.of() : List.copyOf(images);
            renderedPageImages = renderedPageImages == null ? List.of() : List.copyOf(renderedPageImages);
        }
    }
}
