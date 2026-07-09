package com.smartlearnly.backend.flashcard.staging.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartlearnly.backend.flashcard.staging.service.FlashcardDocumentTextExtractionService.DocumentTextExtractionResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.util.Units;
import org.junit.jupiter.api.Test;
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

    private static FlashcardDocumentGenerationProperties properties() {
        return new FlashcardDocumentGenerationProperties();
    }
}
