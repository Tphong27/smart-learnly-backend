package com.smartlearnly.backend.flashcard.staging.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartlearnly.backend.flashcard.staging.service.FlashcardTextGenerationService.GeneratedFlashcardCandidate;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardTextGenerationService.GenerationRequest;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardTextGenerationService.GenerationResult;
import org.junit.jupiter.api.Test;

class RuleBasedFlashcardTextGenerationServiceTest {

    private final RuleBasedFlashcardTextGenerationService service =
            new RuleBasedFlashcardTextGenerationService();

    @Test
    void structuredVietnameseFrontBackIsParsedExactlyWithoutTranslationOrRewrite() {
        String source = String.join("\n\n",
                "Flashcard 1",
                "Front: CRUD là viết tắt của những thao tác nào?",
                "Back: Create - Read - Update - Delete.",
                "Flashcard 2",
                "Front: API Create thường sử dụng HTTP Method nào?",
                "Back: POST."
        );

        GenerationResult result = generate(source, 5, "en", "hard");

        assertThat(result.candidates()).hasSize(2);
        assertThat(result.candidates()).extracting(GeneratedFlashcardCandidate::frontText)
                .containsExactly(
                        "CRUD là viết tắt của những thao tác nào?",
                        "API Create thường sử dụng HTTP Method nào?"
                );
        assertThat(result.candidates()).extracting(GeneratedFlashcardCandidate::backText)
                .containsExactly("Create - Read - Update - Delete.", "POST.");
        assertThat(result.candidates()).extracting(GeneratedFlashcardCandidate::explanation)
                .containsOnlyNulls();
    }

    @Test
    void structuredEnglishFrontBackIsParsedExactly() {
        String source = String.join("\n",
                "Card 1",
                "Front: What does API stand for?",
                "Back: Application Programming Interface.",
                "",
                "Card 2",
                "Front: Which HTTP method usually creates a resource?",
                "Back: POST."
        );

        GenerationResult result = generate(source, 10, "vi", "easy");

        assertThat(result.candidates()).hasSize(2);
        assertThat(result.candidates()).extracting(GeneratedFlashcardCandidate::frontText)
                .containsExactly(
                        "What does API stand for?",
                        "Which HTTP method usually creates a resource?"
                );
        assertThat(result.candidates()).extracting(GeneratedFlashcardCandidate::backText)
                .containsExactly("Application Programming Interface.", "POST.");
    }

    @Test
    void structuredAliasesAreParsed() {
        String source = String.join("\n\n",
                "Flashcard 1\nQ: HTTP Create method?\nA: POST.",
                "Flashcard 2\nQuestion: HTTP Read method?\nAnswer: GET.",
                "Flashcard 3\nCâu hỏi: CRUD có thao tác xóa nào?\nĐáp án: Delete."
        );

        GenerationResult result = generate(source, 10, "en", "medium");

        assertThat(result.candidates()).hasSize(3);
        assertThat(result.candidates()).extracting(GeneratedFlashcardCandidate::frontText)
                .containsExactly(
                        "HTTP Create method?",
                        "HTTP Read method?",
                        "CRUD có thao tác xóa nào?"
                );
        assertThat(result.candidates()).extracting(GeneratedFlashcardCandidate::backText)
                .containsExactly("POST.", "GET.", "Delete.");
    }

    @Test
    void structuredExplanationIsPreservedWhenProvided() {
        String source = String.join("\n",
                "Flashcard 1",
                "Front: API Create thường sử dụng HTTP Method nào?",
                "Back: POST.",
                "Giải thích: POST gửi dữ liệu để tạo tài nguyên mới."
        );

        GenerationResult result = generate(source, 5, "en", "hard");

        assertThat(result.candidates()).hasSize(1);
        assertThat(result.candidates().get(0).explanation())
                .isEqualTo("POST gửi dữ liệu để tạo tài nguyên mới.");
    }

    @Test
    void structuredInputDoesNotReceiveGenericExplanation() {
        String source = String.join("\n",
                "Question: What is REST?",
                "Answer: An architectural style for APIs."
        );

        GenerationResult result = generate(source, 1, "en", "medium");

        assertThat(result.candidates()).hasSize(1);
        assertThat(result.candidates().get(0).explanation()).isNull();
    }

    @Test
    void inlineStructuredCardsAreParsedInOrder() {
        String source = "Flashcard 1 Front: Inline front? Back: Inline back. "
                + "Flashcard 2 Question: Inline question? Answer: Inline answer. "
                + "Explanation: Inline explanation.";

        GenerationResult result = generate(source, 10, "en", "medium");

        assertThat(result.candidates()).hasSize(2);
        assertThat(result.candidates()).extracting(GeneratedFlashcardCandidate::frontText)
                .containsExactly("Inline front?", "Inline question?");
        assertThat(result.candidates()).extracting(GeneratedFlashcardCandidate::backText)
                .containsExactly("Inline back.", "Inline answer.");
        assertThat(result.candidates()).extracting(GeneratedFlashcardCandidate::explanation)
                .containsExactly(null, "Inline explanation.");
    }

    @Test
    void desiredCountLimitsStructuredCards() {
        String source = String.join("\n\n",
                "Flashcard 1\nFront: First?\nBack: First.",
                "Flashcard 2\nFront: Second?\nBack: Second.",
                "Flashcard 3\nFront: Third?\nBack: Third."
        );

        GenerationResult result = generate(source, 2, "en", "medium");

        assertThat(result.candidates()).hasSize(2);
        assertThat(result.candidates()).extracting(GeneratedFlashcardCandidate::frontText)
                .containsExactly("First?", "Second?");
    }

    @Test
    void fallbackEnglishTemplatesFollowDifficulty() {
        String source = "Smart Learnly flashcard lessons help trainees review key course concepts "
                + "after each structured lesson and reinforce important knowledge.";

        assertThat(generate(source, 1, "en", "easy").candidates().get(0).frontText())
                .isEqualTo("What is the main idea of this content?");
        assertThat(generate(source, 1, "en", "medium").candidates().get(0).frontText())
                .startsWith("What is the key idea of: Smart Learnly flashcard lessons");
        assertThat(generate(source, 1, "en", "hard").candidates().get(0).frontText())
                .startsWith("How would you explain or apply this idea: Smart Learnly flashcard lessons");
    }

    @Test
    void fallbackVietnameseTemplatesFollowDifficulty() {
        String source = "Bài học flashcard giúp học viên ôn tập các khái niệm quan trọng "
                + "sau mỗi bài học và củng cố kiến thức cần nhớ trong khóa học.";

        assertThat(generate(source, 1, "vi", "easy").candidates().get(0).frontText())
                .isEqualTo("Ý chính của nội dung này là gì?");
        assertThat(generate(source, 1, "vi", "medium").candidates().get(0).frontText())
                .startsWith("Ý chính của đoạn này là gì: Bài học flashcard giúp học viên");
        assertThat(generate(source, 1, "vi", "hard").candidates().get(0).frontText())
                .startsWith("Có thể giải thích hoặc áp dụng ý này như thế nào: Bài học flashcard giúp học viên");
    }

    @Test
    void fallbackVietnameseTemplatesSupportLanguageAliases() {
        String source = "Spring Boot là một framework mạnh mẽ của Java, được sử dụng rộng rãi "
                + "để phát triển các ứng dụng backend một cách nhanh chóng và hiệu quả.";

        assertThat(generate(source, 1, "Vietnamese", "hard").candidates().get(0).frontText())
                .startsWith("Có thể giải thích hoặc áp dụng ý này như thế nào: Spring Boot là một framework");
        assertThat(generate(source, 1, "vn", "hard").candidates().get(0).frontText())
                .startsWith("Có thể giải thích hoặc áp dụng ý này như thế nào: Spring Boot là một framework");
        assertThat(generate(source, 1, "tieng viet", "hard").candidates().get(0).frontText())
                .startsWith("Có thể giải thích hoặc áp dụng ý này như thế nào: Spring Boot là một framework");
        assertThat(generate(source, 1, "tiếng việt", "hard").candidates().get(0).frontText())
                .startsWith("Có thể giải thích hoặc áp dụng ý này như thế nào: Spring Boot là một framework");
    }

    @Test
    void fallbackKeepsBackAsSourceChunkAndUsesGenericExplanation() {
        String source = "Flashcards convert lesson material into short prompts that help learners "
                + "recall definitions, processes, and examples during repeated practice.";

        GeneratedFlashcardCandidate candidate = generate(source, 1, "en", "medium")
                .candidates()
                .get(0);

        assertThat(candidate.backText()).isEqualTo(source);
        assertThat(candidate.explanation()).isEqualTo("Generated from pasted text.");
    }

    private GenerationResult generate(String sourceText, int desiredCount, String language, String difficulty) {
        return service.generate(new GenerationRequest(
                sourceText,
                desiredCount,
                language,
                difficulty,
                "RULE_BASED"
        ));
    }
}
