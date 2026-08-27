package com.smartlearnly.backend.flashcard.staging.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardDocumentTextExtractionService.DocumentImage;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardDocumentTextExtractionService.DocumentTextExtractionResult;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.util.Units;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.mock.web.MockMultipartFile;

class DefaultFlashcardDocumentTextExtractionServiceTest {
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII="
    );

    private final DefaultFlashcardDocumentTextExtractionService service =
            new DefaultFlashcardDocumentTextExtractionService(properties());

    @Test
    void extractDocxTextAndEmbeddedRasterImages() throws Exception {
        byte[] docx = docxWithTextAndImage(
                "Document import should extract selectable DOCX text for flashcard creation."
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "lesson.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docx
        );

        DocumentTextExtractionResult result = service.extract(file);

        assertThat(result.sourceType()).isEqualTo("DOCX");
        assertThat(result.sourceName()).isEqualTo("lesson.docx");
        assertThat(result.text()).contains("Document import should extract selectable DOCX text");
        assertThat(result.images()).hasSize(1);
        assertThat(result.renderedPageImages()).isEmpty();
        assertThat(result.images().get(0).contentType()).isEqualTo("image/png");
        assertThat(result.images().get(0).content()).isNotEmpty();
    }

    @Test
    void extractDocxSkipsEmbeddedImagesWhenSelectableTextIsSufficient() throws Exception {
        byte[] docx = docxWithTextAndImage("""
                Document import should use selectable DOCX text when the text already contains enough readable learning
                content for flashcard creation. Embedded images are only needed when the document text is too short or
                missing, so this document should not return image payloads for the next step.
                """);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "lesson.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docx
        );

        DocumentTextExtractionResult result = service.extract(file);

        assertThat(result.text()).contains("selectable DOCX text");
        assertThat(result.images()).isEmpty();
        assertThat(result.renderedPageImages()).isEmpty();
    }

    @Test
    void extractPdfRendersLimitedPagesWhenSelectableTextIsInsufficient() throws Exception {
        byte[] pdf = blankPdf(5);
        MockMultipartFile file = new MockMultipartFile("file", "scan.pdf", "application/pdf", pdf);

        DocumentTextExtractionResult result = service.extract(file);

        assertThat(result.sourceType()).isEqualTo("PDF");
        assertThat(result.text()).isBlank();
        assertThat(result.images()).isEmpty();
        assertThat(result.renderedPageImages()).hasSize(3);
        assertThat(result.renderedPageImages()).allSatisfy(image -> {
            assertThat(image.contentType()).isEqualTo("image/jpeg");
            assertThat(image.content()).isNotEmpty();
        });
    }

    @Test
    void extractPdfRenderedPagesRespectConfiguredLimit() throws Exception {
        FlashcardDocumentGenerationProperties properties = properties();
        properties.setMaxRenderedPdfPages(1);
        DefaultFlashcardDocumentTextExtractionService limitedService =
                new DefaultFlashcardDocumentTextExtractionService(properties);
        byte[] pdf = blankPdf(4);
        MockMultipartFile file = new MockMultipartFile("file", "scan.pdf", "application/pdf", pdf);

        DocumentTextExtractionResult result = limitedService.extract(file);

        assertThat(result.renderedPageImages()).hasSize(1);
    }

    @Test
    void extractPdfSkipsImagesAndRenderedPagesWhenSelectableTextIsSufficient() throws Exception {
        byte[] pdf = pdfWithText("""
                Flashcard document generation should use selectable PDF text when the page already includes enough
                readable educational content. In that case, embedded image reading and rendered page OCR should be
                skipped so the document path stays fast and conservative with external image reading calls.
                """);
        MockMultipartFile file = new MockMultipartFile("file", "lesson.pdf", "application/pdf", pdf);

        DocumentTextExtractionResult result = service.extract(file);

        assertThat(result.text()).contains("selectable PDF text");
        assertThat(result.images()).isEmpty();
        assertThat(result.renderedPageImages()).isEmpty();
    }

    @Test
    void extractRejectsInvalidUploadedFileNamesAndUnsupportedExtensions() {
        assertInvalidFileName(null, "Uploaded file name is required");
        assertInvalidFileName("   ", "Uploaded file name is required");
        assertInvalidFileName("lesson..pdf", "Uploaded file name is invalid");
        assertInvalidFileName("lesson", "Uploaded file must be a DOCX or PDF file");
        assertInvalidFileName("lesson.", "Uploaded file must be a DOCX or PDF file");
        assertInvalidFileName("lesson.txt", "Unsupported flashcard source file type");
    }

    @Test
    void extractRejectsDocumentLargerThanConfiguredLimitBeforeReadingContent() {
        FlashcardDocumentGenerationProperties properties = properties();
        properties.setMaxSourceFileSize(DataSize.ofBytes(3));
        DefaultFlashcardDocumentTextExtractionService limitedService =
                new DefaultFlashcardDocumentTextExtractionService(properties);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "lesson.pdf",
                "application/pdf",
                new byte[] { 1, 2, 3, 4 });

        assertThatThrownBy(() -> limitedService.extract(file))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST))
                .hasMessage("Uploaded document must not exceed 1 MB");
    }

    @Test
    void extractSanitizesPathLikeFileNameToBaseName() throws Exception {
        byte[] docx = docxWithParagraphs("Path based upload name should keep only the base document name.");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "C:\\training\\week-one\\lesson.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docx
        );

        DocumentTextExtractionResult result = service.extract(file);

        assertThat(result.sourceName()).isEqualTo("lesson.docx");
        assertThat(result.sourceType()).isEqualTo("DOCX");
        assertThat(result.text()).contains("Path based upload name");
    }

    @Test
    void extractWrapsMultipartReadFailureAsInvalidRequest() {
        MultipartFile unreadable = unreadableFile("lesson.pdf");

        assertBusinessException(
                () -> service.extract(unreadable),
                "Uploaded file could not be read"
        );
    }

    @Test
    void extractRejectsMalformedPdfAndDocxContent() {
        assertBusinessException(
                () -> service.extract(new MockMultipartFile("file", "broken.pdf", "application/pdf", "not a pdf".getBytes())),
                "PDF text could not be extracted"
        );
        assertBusinessException(
                () -> service.extract(new MockMultipartFile(
                        "file",
                        "broken.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "not a docx".getBytes()
                )),
                "DOCX text could not be extracted"
        );
    }

    @Test
    void extractDocxIncludesTableRowsAndNormalizesReadableBlocks() throws Exception {
        byte[] docx = docxWithParagraphsAndTable();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "tables.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docx
        );

        DocumentTextExtractionResult result = service.extract(file);

        assertThat(result.text()).contains("Intro paragraph with tabs and spaces.");
        assertThat(result.text()).contains("Second paragraph keeps a readable boundary.");
        assertThat(result.text()).contains("Term | Definition");
        assertThat(result.text()).contains("Blank middle cell | Answer");
        assertThat(result.text()).doesNotContain("\t");
        assertThat(result.images()).isEmpty();
    }

    @Test
    void extractDocxEmbeddedImagesRespectConfiguredLimitsAndContentTypes() throws Exception {
        FlashcardDocumentGenerationProperties properties = properties();
        properties.setMaxEmbeddedImages(1);
        DefaultFlashcardDocumentTextExtractionService limitedService =
                new DefaultFlashcardDocumentTextExtractionService(properties);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "images.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxWithTwoPngImagesAndUnsupportedImage()
        );

        DocumentTextExtractionResult limitedResult = limitedService.extract(file);

        assertThat(limitedResult.images()).hasSize(1);
        assertThat(limitedResult.images().get(0).contentType()).isEqualTo("image/png");
        assertThat(limitedResult.images().get(0).fileName()).doesNotContain("..");
        assertThat(limitedResult.images().get(0).fileName()).endsWith(".png");

        FlashcardDocumentGenerationProperties disabledProperties = properties();
        disabledProperties.setMaxEmbeddedImages(0);
        DefaultFlashcardDocumentTextExtractionService disabledService =
                new DefaultFlashcardDocumentTextExtractionService(disabledProperties);

        DocumentTextExtractionResult disabledResult = disabledService.extract(file);

        assertThat(disabledResult.images()).isEmpty();
    }

    @Test
    void extractDocxSkipsEmbeddedImagesOverConfiguredSize() throws Exception {
        FlashcardDocumentGenerationProperties properties = properties();
        properties.setMaxEmbeddedImageSize(DataSize.ofBytes(PNG.length - 1L));
        DefaultFlashcardDocumentTextExtractionService tinyImageLimitService =
                new DefaultFlashcardDocumentTextExtractionService(properties);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large-image.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxWithTextAndImage("Short")
        );

        DocumentTextExtractionResult result = tinyImageLimitService.extract(file);

        assertThat(result.text()).isEqualTo("Short");
        assertThat(result.images()).isEmpty();
    }

    @Test
    void extractPdfReturnsEmbeddedImagesFromPageAndNestedFormResources() throws Exception {
        byte[] pdf = pdfWithEmbeddedImages();
        MockMultipartFile file = new MockMultipartFile("file", "scan.pdf", "application/pdf", pdf);

        DocumentTextExtractionResult result = service.extract(file);

        assertThat(result.text()).isBlank();
        assertThat(result.images()).hasSize(2);
        assertThat(result.images()).allSatisfy(image -> {
            assertThat(image.contentType()).isEqualTo("image/png");
            assertThat(image.fileName()).doesNotContain("..");
            assertThat(image.fileName()).endsWith(".png");
            assertThat(image.content()).isNotEmpty();
        });
        assertThat(result.renderedPageImages()).hasSize(1);
    }

    @Test
    void extractPdfEmbeddedAndRenderedImagesRespectConfiguredLimits() throws Exception {
        FlashcardDocumentGenerationProperties properties = properties();
        properties.setMaxEmbeddedImages(1);
        properties.setMaxRenderedPdfPages(0);
        DefaultFlashcardDocumentTextExtractionService limitedService =
                new DefaultFlashcardDocumentTextExtractionService(properties);
        MockMultipartFile file = new MockMultipartFile("file", "scan.pdf", "application/pdf", pdfWithEmbeddedImages());

        DocumentTextExtractionResult result = limitedService.extract(file);

        assertThat(result.images()).hasSize(1);
        assertThat(result.renderedPageImages()).isEmpty();
    }

    @Test
    void extractPdfOmitsRenderedPagesWhenEncodedImageCannotFitConfiguredSize() throws Exception {
        FlashcardDocumentGenerationProperties properties = properties();
        properties.setMaxEmbeddedImages(0);
        properties.setMaxRenderedPdfPages(1);
        properties.setMaxRenderedPageImageSize(DataSize.ofBytes(1));
        DefaultFlashcardDocumentTextExtractionService tinyPageLimitService =
                new DefaultFlashcardDocumentTextExtractionService(properties);
        MockMultipartFile file = new MockMultipartFile("file", "scan.pdf", "application/pdf", blankPdf(1));

        DocumentTextExtractionResult result = tinyPageLimitService.extract(file);

        assertThat(result.images()).isEmpty();
        assertThat(result.renderedPageImages()).isEmpty();
    }

    private byte[] docxWithTextAndImage(String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph()
                    .createRun()
                    .setText(text);
            XWPFRun run = document.createParagraph().createRun();
            run.addPicture(
                    new ByteArrayInputStream(PNG),
                    Document.PICTURE_TYPE_PNG,
                    "embedded.png",
                    Units.toEMU(1),
                    Units.toEMU(1)
            );
            document.write(output);
            return output.toByteArray();
        }
    }

    private byte[] docxWithParagraphs(String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph()
                    .createRun()
                    .setText(text);
            document.write(output);
            return output.toByteArray();
        }
    }

    private byte[] docxWithParagraphsAndTable() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(" Intro\u00a0paragraph\twith   tabs and spaces. ");
            document.createParagraph().createRun().setText("   ");
            document.createParagraph().createRun().setText("Second paragraph keeps a readable boundary.");
            XWPFTable table = document.createTable(2, 3);
            table.getRow(0).getCell(0).setText("Term");
            table.getRow(0).getCell(1).setText("Definition");
            table.getRow(0).getCell(2).setText(" ");
            table.getRow(1).getCell(0).setText("Blank middle cell");
            table.getRow(1).getCell(1).setText(" ");
            table.getRow(1).getCell(2).setText("Answer");
            document.write(output);
            return output.toByteArray();
        }
    }

    private byte[] docxWithTwoPngImagesAndUnsupportedImage() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Short");
            XWPFRun first = document.createParagraph().createRun();
            first.addPicture(new ByteArrayInputStream(PNG), Document.PICTURE_TYPE_PNG, "first.png", Units.toEMU(1), Units.toEMU(1));
            XWPFRun unsupported = document.createParagraph().createRun();
            unsupported.addPicture(new ByteArrayInputStream(PNG), Document.PICTURE_TYPE_EMF, "diagram.emf", Units.toEMU(1), Units.toEMU(1));
            XWPFRun second = document.createParagraph().createRun();
            second.addPicture(new ByteArrayInputStream(PNG), Document.PICTURE_TYPE_PNG, "second.png", Units.toEMU(1), Units.toEMU(1));
            document.write(output);
            return output.toByteArray();
        }
    }

    private byte[] blankPdf(int pages) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int index = 0; index < pages; index += 1) {
                document.addPage(new PDPage());
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] pdfWithText(String text) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(48, 720);
                for (String line : text.replace('\r', '\n').split("\\n")) {
                    String normalized = line.trim();
                    if (!normalized.isBlank()) {
                        content.showText(normalized);
                        content.newLineAtOffset(0, -16);
                    }
                }
                content.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] pdfWithEmbeddedImages() throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            PDResources resources = new PDResources();
            PDImageXObject pageImage = LosslessFactory.createFromImage(document, coloredImage(Color.BLUE));
            resources.put(COSName.getPDFName("PageImage"), pageImage);

            PDFormXObject form = new PDFormXObject(document);
            PDResources formResources = new PDResources();
            formResources.put(COSName.getPDFName("NestedImage"), LosslessFactory.createFromImage(document, coloredImage(Color.RED)));
            form.setResources(formResources);
            resources.put(COSName.getPDFName("NestedForm"), form);

            page.setResources(resources);
            document.save(output);
            return output.toByteArray();
        }
    }

    private BufferedImage coloredImage(Color color) {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < image.getWidth(); x += 1) {
            for (int y = 0; y < image.getHeight(); y += 1) {
                image.setRGB(x, y, color.getRGB());
            }
        }
        return image;
    }

    private MultipartFile unreadableFile(String originalFilename) {
        return new MultipartFile() {
            @Override
            public String getName() {
                return "file";
            }

            @Override
            public String getOriginalFilename() {
                return originalFilename;
            }

            @Override
            public String getContentType() {
                return "application/pdf";
            }

            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public long getSize() {
                return 1;
            }

            @Override
            public byte[] getBytes() throws IOException {
                throw new IOException("cannot read");
            }

            @Override
            public java.io.InputStream getInputStream() throws IOException {
                throw new IOException("cannot read");
            }

            @Override
            public void transferTo(java.io.File dest) {
            }
        };
    }

    private void assertInvalidFileName(String originalFilename, String message) {
        assertBusinessException(
                () -> service.extract(new MockMultipartFile("file", originalFilename, "application/octet-stream", PNG)),
                message
        );
    }

    private void assertBusinessException(Runnable action, String message) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST))
                .hasMessage(message);
    }

    private static FlashcardDocumentGenerationProperties properties() {
        return new FlashcardDocumentGenerationProperties();
    }
}
