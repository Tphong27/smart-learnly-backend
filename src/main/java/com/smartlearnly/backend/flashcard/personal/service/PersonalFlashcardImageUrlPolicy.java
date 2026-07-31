package com.smartlearnly.backend.flashcard.personal.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.file.config.StorageProperties;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PersonalFlashcardImageUrlPolicy {
    private final StorageProperties storageProperties;

    public void validateNewOrUnchanged(String candidateUrl, String existingUrl, UUID setId) {
        if (candidateUrl == null || candidateUrl.equals(existingUrl)) {
            return;
        }
        if (!candidateUrl.startsWith(expectedPrefix(setId))) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Flashcard image URL must come from the owned flashcard set image storage path"
            );
        }
    }

    private String expectedPrefix(UUID setId) {
        String imagePath = "flashcard-sets/" + setId + "/images/";
        String provider = storageProperties.getProvider() == null
                ? "supabase"
                : storageProperties.getProvider().trim().toLowerCase(Locale.ROOT);

        return switch (provider) {
            case "supabase" -> normalizeBase(storageProperties.getSupabaseUrl())
                    + "/storage/v1/object/public/"
                    + storageProperties.getLessonResourceBucket()
                    + "/"
                    + imagePath;
            case "r2" -> normalizeBase(firstConfigured(
                    storageProperties.getR2LessonResourcePublicUrl(),
                    storageProperties.getR2PublicUrl()
            )) + "/" + imagePath;
            default -> throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Configured file storage provider is not supported for flashcard images"
            );
        };
    }

    private String firstConfigured(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
    }

    private String normalizeBase(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Flashcard image storage public URL is not configured"
            );
        }
        return value.trim().replaceAll("/+$", "");
    }
}
