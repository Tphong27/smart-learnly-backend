package com.smartlearnly.backend.question.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.file.config.StorageProperties;
import com.smartlearnly.backend.file.service.FileStorageService;
import com.smartlearnly.backend.question.entity.Question;
import com.smartlearnly.backend.question.entity.QuestionMediaType;
import com.smartlearnly.backend.question.repository.QuestionMediaAttachmentRepository;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuestionMediaImportServiceTest {

    @Mock
    private QuestionMediaAttachmentRepository mediaAttachmentRepository;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private StorageProperties storageProperties;

    private QuestionMediaImportService service;
    private Question question;

    @BeforeEach
    void setUp() {
        service = new QuestionMediaImportService(
                mediaAttachmentRepository,
                fileStorageService,
                storageProperties
        );
        question = new Question();
        question.setId(UUID.randomUUID());
    }

    @Test
    void validateMediaReferences_returnsEmpty_whenNoMediaUrlsAreProvided() {
        List<String> errors = service.validateMediaReferences(null, Arrays.asList(" ", null));

        assertThat(errors).isEmpty();
    }

    @Test
    void validateMediaReferences_reportsMaxImageCountBeforeParsingUrls() {
        List<String> errors = service.validateMediaReferences(
                List.of("bad-1", "bad-2", "bad-3", "bad-4", "bad-5", "bad-6"),
                null);

        assertThat(errors).containsExactly("A question can have at most 5 images");
    }

    @Test
    void validateMediaReferences_reportsInvalidScheme() {
        List<String> errors = service.validateMediaReferences(
                List.of("ftp://example.com/question.png"),
                null);

        assertThat(errors).singleElement()
                .asString()
                .contains("Image URL is invalid")
                .contains("Media URL must use http or https");
    }

    @Test
    void validateMediaReferences_reportsCredentialsInUrl() {
        List<String> errors = service.validateMediaReferences(
                List.of("https://user:secret@example.com/question.png"),
                null);

        assertThat(errors).singleElement()
                .asString()
                .contains("Media URL must not include credentials");
    }

    @Test
    void validateMediaReferences_reportsLocalhostHost() {
        List<String> errors = service.validateMediaReferences(
                null,
                List.of("https://localhost/question.mp3"));

        assertThat(errors).singleElement()
                .asString()
                .contains("Audio URL is invalid")
                .contains("Media URL host is not allowed");
    }

    @Test
    void attachImportedMedia_returnsWithoutStorage_whenNoUrlsAreProvided() {
        service.attachImportedMedia(question, List.of(), List.of(" "), "excel_import");

        verify(mediaAttachmentRepository, never()).countByQuestionIdAndMediaType(
                question.getId(),
                QuestionMediaType.IMAGE);
        verify(fileStorageService, never()).store(null, null, null, null);
    }

    @Test
    void attachImportedMedia_throwsBusinessRuleViolation_whenExistingImagesExceedLimit() {
        when(mediaAttachmentRepository.countByQuestionIdAndMediaType(question.getId(), QuestionMediaType.IMAGE))
                .thenReturn(5L);

        assertThatThrownBy(() -> service.attachImportedMedia(
                question,
                List.of("https://example.com/question.png"),
                null,
                "excel_import"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION))
                .hasMessageContaining("at most 5 images");

        verify(fileStorageService, never()).store(null, null, null, null);
    }
}
