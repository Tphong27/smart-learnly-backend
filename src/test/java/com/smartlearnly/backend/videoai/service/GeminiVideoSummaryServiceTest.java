package com.smartlearnly.backend.videoai.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.AssignmentAiSettings;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.videoai.config.VideoAiGenerationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeminiVideoSummaryServiceTest {

    @Mock
    private SystemSettingsService settingsService;

    private VideoAiGenerationProperties properties;
    private GeminiVideoSummaryService service;

    @BeforeEach
    void setUp() {
        properties = properties();
        service = new GeminiVideoSummaryService(
                properties,
                settingsService,
                new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void generateSummaryFromTranscript_rejectsBlankTranscript() {
        stubHealthySettings();

        assertErrorCode(
                () -> service.generateSummaryFromTranscript("en", "   "),
                ErrorCode.INVALID_REQUEST);
    }

    @Test
    void generateSummaryFromTranscript_rejectsNullTranscript() {
        stubHealthySettings();

        assertErrorCode(
                () -> service.generateSummaryFromTranscript("en", null),
                ErrorCode.INVALID_REQUEST);
    }

    @Test
    void generateSummaryFromYoutubeVideo_rejectsBlankUrl() {
        stubHealthySettings();

        assertErrorCode(
                () -> service.generateSummaryFromYoutubeVideo("  "),
                ErrorCode.INVALID_REQUEST);
    }

    @Test
    void generateSummaryFromYoutubeVideo_rejectsNullUrl() {
        stubHealthySettings();

        assertErrorCode(
                () -> service.generateSummaryFromYoutubeVideo(null),
                ErrorCode.INVALID_REQUEST);
    }

    @Test
    void ensureAvailable_rejectsWhenAiDisabled() {
        when(settingsService.resolveAssignmentAiSettings())
                .thenReturn(new AssignmentAiSettings(
                        false, "gemini", "key", "gemini-flash", null, 30));

        assertErrorCode(
                () -> service.generateSummaryFromTranscript("en", "Lesson transcript"),
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
    }

    @Test
    void ensureAvailable_rejectsWhenProviderIsNotGemini() {
        when(settingsService.resolveAssignmentAiSettings())
                .thenReturn(new AssignmentAiSettings(
                        true, "openai", "key", "gemini-flash", null, 30));

        assertErrorCode(
                () -> service.generateSummaryFromTranscript("en", "Lesson transcript"),
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
    }

    @Test
    void ensureAvailable_rejectsWhenApiKeyIsBlank() {
        when(settingsService.resolveAssignmentAiSettings())
                .thenReturn(new AssignmentAiSettings(
                        true, "gemini", "  ", "gemini-flash", null, 30));

        assertErrorCode(
                () -> service.generateSummaryFromTranscript("en", "Lesson transcript"),
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
    }

    @Test
    void ensureAvailable_rejectsWhenApiKeyIsNull() {
        when(settingsService.resolveAssignmentAiSettings())
                .thenReturn(new AssignmentAiSettings(
                        true, "gemini", null, "gemini-flash", null, 30));

        assertErrorCode(
                () -> service.generateSummaryFromTranscript("en", "Lesson transcript"),
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
    }

    @Test
    void ensureAvailable_rejectsWhenModelIsBlank() {
        when(settingsService.resolveAssignmentAiSettings())
                .thenReturn(new AssignmentAiSettings(
                        true, "gemini", "key", "  ", null, 30));

        assertErrorCode(
                () -> service.generateSummaryFromTranscript("en", "Lesson transcript"),
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
    }

    @Test
    void ensureAvailable_rejectsWhenApiKeyContainsPlaceholderBrackets() {
        when(settingsService.resolveAssignmentAiSettings())
                .thenReturn(new AssignmentAiSettings(
                        true, "gemini", "<your-api-key>", "gemini-flash", null, 30));

        assertErrorCode(
                () -> service.generateSummaryFromTranscript("en", "Lesson transcript"),
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
    }

    private void stubHealthySettings() {
        when(settingsService.resolveAssignmentAiSettings())
                .thenReturn(new AssignmentAiSettings(
                        true, "gemini", "gemini-test-key", "gemini-flash", null, 30));
    }

    private VideoAiGenerationProperties properties() {
        VideoAiGenerationProperties props = new VideoAiGenerationProperties();
        props.setEnabled(true);
        props.setApiKey("gemini-test-key");
        props.setApiBaseUrl("https://gemini.example.test/v1beta");
        props.setModel("gemini-test");
        return props;
    }

    private void assertErrorCode(ThrowingOperation operation, ErrorCode expected) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(expected);
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws Exception;
    }
}
