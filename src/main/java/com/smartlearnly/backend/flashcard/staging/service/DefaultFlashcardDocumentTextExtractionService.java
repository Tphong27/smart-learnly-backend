package com.smartlearnly.backend.flashcard.staging.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardDocumentTextExtractionService.DocumentImage;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardDocumentTextExtractionService.DocumentTextExtractionResult;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DefaultFlashcardDocumentTextExtractionService implements FlashcardDocumentTextExtractionService {
    private static final String SOURCE_TYPE_DOCX = "DOCX";
    private static final String SOURCE_TYPE_PDF = "PDF";
    private static final String IMAGE_TYPE_PNG = "image/png";
    private static final int MAX_EMBEDDED_IMAGES = 5;
    private static final int MAX_PDF_IMAGE_RECURSION_DEPTH = 4;
    private static final long MAX_EMBEDDED_IMAGE_BYTES = 10L * 1024L * 1024L;
    private static final Set<String> SUPPORTED_EMBEDDED_IMAGE_TYPES = Set.of(
            IMAGE_TYPE_PNG,
            "image/jpeg",
            "image/webp"
    );

    @Override
    public DocumentTextExtractionResult extract(MultipartFile file) {
        String fileName = sanitizeOriginalFileName(file.getOriginalFilename());
        String extension = extractExtension(fileName);
        byte[] content = readBytes(file);
        ExtractionContent extraction = switch (extension) {
            case "pdf" -> extractPdfContent(content);
            case "docx" -> extractDocxContent(content);
            default -> throw new BusinessException(ErrorCode.INVALID_REQUEST, "Unsupported flashcard source file type");
        };
        String sourceType = extension.toUpperCase(Locale.ROOT);
        return new DocumentTextExtractionResult(
                sourceType,
                fileName,
                normalizeExtractedText(extraction.text()),
                extraction.images()
        );
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
            return new ExtractionContent(String.join("\n\n", pages), extractPdfImages(document));
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
            return new ExtractionContent(String.join("\n\n", blocks), extractDocxImages(document));
        }
        catch (IOException | RuntimeException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "DOCX text could not be extracted");
        }
    }

    private List<DocumentImage> extractDocxImages(XWPFDocument document) {
        List<DocumentImage> images = new ArrayList<>();
        for (XWPFPictureData picture : document.getAllPictures()) {
            if (images.size() >= MAX_EMBEDDED_IMAGES) {
                break;
            }
            byte[] data = picture.getData();
            if (data == null || data.length == 0 || data.length > MAX_EMBEDDED_IMAGE_BYTES) {
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
            if (images.size() >= MAX_EMBEDDED_IMAGES) {
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
        if (resources == null || images.size() >= MAX_EMBEDDED_IMAGES || depth > MAX_PDF_IMAGE_RECURSION_DEPTH) {
            return;
        }
        for (COSName xObjectName : resources.getXObjectNames()) {
            if (images.size() >= MAX_EMBEDDED_IMAGES) {
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
        if (data.length == 0 || data.length > MAX_EMBEDDED_IMAGE_BYTES) {
            return;
        }
        images.add(new DocumentImage(
                sanitizeEmbeddedImageName(fileName, "pdf-image-" + (images.size() + 1) + ".png"),
                IMAGE_TYPE_PNG,
                data
        ));
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

    private record ExtractionContent(String text, List<DocumentImage> images) {
        private ExtractionContent {
            images = images == null ? List.of() : List.copyOf(images);
        }
    }
}
