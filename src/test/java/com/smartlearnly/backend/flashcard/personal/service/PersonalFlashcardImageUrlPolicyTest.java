package com.smartlearnly.backend.flashcard.personal.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.file.config.StorageProperties;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PersonalFlashcardImageUrlPolicyTest {
    @Test
    void shouldAllowOnlyConfiguredOwnedSetImageUrlsForNewImages() {
        StorageProperties properties = new StorageProperties();
        properties.setR2LessonResourcePublicUrl("https://resources.example.test");
        PersonalFlashcardImageUrlPolicy policy = new PersonalFlashcardImageUrlPolicy(properties);
        UUID setId = UUID.randomUUID();
        String ownedImageUrl = "https://resources.example.test/flashcard-sets/"
                + setId + "/images/card.png";

        assertThatCode(() -> policy.validateNewOrUnchanged(ownedImageUrl, null, setId))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.validateNewOrUnchanged(
                "https://example.test/image.png", null, setId
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                org.assertj.core.api.Assertions.assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        assertThatThrownBy(() -> policy.validateNewOrUnchanged(
                "https://resources.example.test/flashcard-sets/"
                        + UUID.randomUUID() + "/images/card.png",
                null,
                setId
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                org.assertj.core.api.Assertions.assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    void shouldRetainAnUnchangedExistingUrlWithoutTreatingItAsANewExternalUrl() {
        StorageProperties properties = new StorageProperties();
        PersonalFlashcardImageUrlPolicy policy = new PersonalFlashcardImageUrlPolicy(properties);
        String existingUrl = "https://legacy.example.test/image.png";

        assertThatCode(() -> policy.validateNewOrUnchanged(existingUrl, existingUrl, UUID.randomUUID()))
                .doesNotThrowAnyException();
    }
}
