package com.smartlearnly.backend.question.ai.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.admin.settings.service.SystemSettingsService;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.AssignmentAiSettings;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GeminiQuestionGenerationProviderTest {

    @Mock
    private SystemSettingsService settingsService;

    private QuestionAiGenerationProperties properties;
    private GeminiQuestionGenerationProvider provider;

    @BeforeEach
    void setUp() {
        properties = new QuestionAiGenerationProperties();
        properties.setEnabled(true);
        properties.setProvider("gemini");
        properties.setApiKey("test-key");
        properties.setApiBaseUrl("https://gemini.example.test/v1beta");
        properties.setModel("gemini-primary");
        properties.setFallbackModel(null);
        properties.setTimeout(Duration.ofSeconds(2));

        provider = new GeminiQuestionGenerationProvider(properties, settingsService);
    }

    @Test
    void generate_rejectsUnavailableConfigurationsBeforeCallingProvider() {
        assertUnavailable(
                () -> new AssignmentAiSettings(false, "gemini", "key", "gemini-primary", null, 30),
                "disabled");
        assertUnavailable(
                () -> new AssignmentAiSettings(true, "openai", "key", "gemini-primary", null, 30),
                "provider is not configured");
        assertUnavailable(
                () -> new AssignmentAiSettings(true, "gemini", null, "gemini-primary", null, 30),
                "API key is not configured");
        assertUnavailable(
                () -> new AssignmentAiSettings(true, "gemini", "  ", "gemini-primary", null, 30),
                "API key is not configured");
        assertUnavailable(
                () -> new AssignmentAiSettings(true, "gemini", "<your-api-key>", "gemini-primary", null, 30),
                "placeholder");
    }

    @Test
    void metadataMethods_returnProviderModelAndPromptVersion() {
        when(settingsService.resolveAssignmentAiSettings())
                .thenReturn(new AssignmentAiSettings(
                        true, "gemini", "test-key", "gemini-primary", null, 30));

        assertThat(provider.providerName()).isEqualTo("gemini");
        assertThat(provider.modelName()).isEqualTo("gemini-primary");
        assertThat(provider.promptVersion()).isEqualTo("question-ai-generation-v1");
    }

    @Test
    void modelName_normalizesModelsPrefixAndBlankDefault() {
        when(settingsService.resolveAssignmentAiSettings())
                .thenReturn(new AssignmentAiSettings(
                        true, "gemini", "test-key", "models/gemini-primary", null, 30))
                .thenReturn(new AssignmentAiSettings(
                        true, "gemini", "test-key", "  ", null, 30));

        assertThat(provider.modelName()).isEqualTo("gemini-primary");
        assertThat(provider.modelName()).isEqualTo("gemini-3.5-flash");
    }

    private void assertUnavailable(Supplier<AssignmentAiSettings> settings, String messageFragment) {
        when(settingsService.resolveAssignmentAiSettings()).thenReturn(settings.get());

        assertThatThrownBy(() -> provider.generate(requestWithSources()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_PROVIDER_UNAVAILABLE);
                    assertThat(exception.getMessage()).containsIgnoringCase(messageFragment);
                });
    }

    private QuestionGenerationProvider.GenerationRequest requestWithSources() {
        UUID sourceId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();
        return new QuestionGenerationProvider.GenerationRequest(
                UUID.randomUUID(),
                3,
                List.of("multiple_choice"),
                "en",
                "Generate grounded questions.",
                List.of(new QuestionGenerationProvider.SourceInput(
                        sourceId,
                        "Lesson transcript",
                        "checksum",
                        "1",
                        List.of(new QuestionGenerationProvider.ChunkInput(
                                chunkId,
                                "00:00-00:10",
                                "A short grounded source excerpt.")))));
    }
}
