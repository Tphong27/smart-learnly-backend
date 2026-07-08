package com.smartlearnly.backend.flashcard.staging.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartlearnly.backend.flashcard.staging.service.FlashcardDocumentTextExtractionService.DocumentTextExtractionResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
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
            new DefaultFlashcardDocumentTextExtractionService();

    @Test
    void extractDocxTextAndEmbeddedRasterImages() throws Exception {
        byte[] docx = docxWithTextAndImage();
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
        assertThat(result.images().get(0).contentType()).isEqualTo("image/png");
        assertThat(result.images().get(0).content()).isNotEmpty();
    }

    private byte[] docxWithTextAndImage() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph()
                    .createRun()
                    .setText("Document import should extract selectable DOCX text for flashcard creation.");
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
}
