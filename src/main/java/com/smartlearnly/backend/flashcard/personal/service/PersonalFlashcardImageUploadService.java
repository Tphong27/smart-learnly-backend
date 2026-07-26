package com.smartlearnly.backend.flashcard.personal.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.file.config.StorageProperties;
import com.smartlearnly.backend.file.service.FileStorageService;
import com.smartlearnly.backend.flashcard.dto.FlashcardImageUploadResponse;
import com.smartlearnly.backend.flashcard.entity.FlashcardSet;
import com.smartlearnly.backend.flashcard.repository.FlashcardSetRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class PersonalFlashcardImageUploadService {
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    private final FlashcardSetRepository flashcardSetRepository;
    private final CurrentUserService currentUserService;
    private final FileStorageService fileStorageService;
    private final StorageProperties storageProperties;
    private final Tika tika = new Tika();

    public FlashcardImageUploadResponse upload(UUID setId, MultipartFile file) {
        UserAccount actor = requireEligibleActor();
        FlashcardSet flashcardSet = flashcardSetRepository.findPersonalByIdAndOwnerId(setId, actor.getId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Personal flashcard set was not found"
                ));

        byte[] content = readAndValidateSize(file);
        String contentType = detectContentType(content);
        String extension = ALLOWED_TYPES.get(contentType);
        if (extension == null) {
            throw new BusinessException(
                    ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "Flashcard image must be JPEG, PNG, or WebP"
            );
        }

        String objectPath = "flashcard-sets/%s/images/%s.%s".formatted(flashcardSet.getId(), UUID.randomUUID(), extension);
        FileStorageService.StoredFile stored = fileStorageService.store(
                storageProperties.getLessonResourceBucket(),
                objectPath,
                contentType,
                content
        );
        return new FlashcardImageUploadResponse(stored.url());
    }

    private UserAccount requireEligibleActor() {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        String role = actor.getRole() == null ? "" : actor.getRole().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("TRAINEE", "TRAINER", "SME").contains(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "You are not allowed to use Personal Flashcards");
        }
        return actor;
    }

    private byte[] readAndValidateSize(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Flashcard image file is required");
        }
        if (file.getSize() > storageProperties.getQuestionImageMaxSize().toBytes()) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE);
        }
        try {
            byte[] content = file.getBytes();
            if (content.length == 0) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Flashcard image file is required");
            }
            return content;
        }
        catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Flashcard image file could not be read");
        }
    }

    private String detectContentType(byte[] content) {
        try {
            return tika.detect(content);
        }
        catch (Exception exception) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Flashcard image type could not be detected");
        }
    }
}
