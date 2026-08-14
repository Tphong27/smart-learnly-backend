package com.smartlearnly.backend.flashcard.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.file.config.StorageProperties;
import com.smartlearnly.backend.file.service.FileStorageService;
import com.smartlearnly.backend.flashcard.dto.FlashcardImageUploadResponse;
import com.smartlearnly.backend.flashcard.entity.FlashcardSet;
import com.smartlearnly.backend.flashcard.repository.FlashcardSetRepository;
import com.smartlearnly.backend.learning.lesson.entity.Lesson;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class FlashcardImageUploadService {
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    private final FlashcardSetRepository flashcardSetRepository;
    private final CurriculumLessonRepository curriculumLessonRepository;
    private final CurrentUserService currentUserService;
    private final FileStorageService fileStorageService;
    private final StorageProperties storageProperties;
    private final Tika tika = new Tika();

    /** Tải ảnh cho master flashcard sau khi kiểm tra quyền chỉnh sửa course. */
    @Transactional(readOnly = true)
    public FlashcardImageUploadResponse upload(UUID setId, MultipartFile file) {
        FlashcardSet flashcardSet = flashcardSetRepository.findByIdAndDeletedAtIsNull(setId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard set was not found"));
        Course course = requireFlashcardCourse(flashcardSet);
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        requireEditAccess(actor, course);

        return uploadOwnedSet(flashcardSet, file);
    }

    /** Lưu ảnh cho flashcard set đã được service gọi kiểm tra ownership trước đó. */
    public FlashcardImageUploadResponse uploadOwnedSet(FlashcardSet flashcardSet, MultipartFile file) {
        if (flashcardSet == null || flashcardSet.getId() == null || flashcardSet.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard set was not found");
        }
        byte[] content = readAndValidateSize(file);
        String contentType = detectContentType(content);
        String extension = ALLOWED_TYPES.get(contentType);
        if (extension == null) {
            throw new BusinessException(
                    ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "Flashcard image must be JPEG, PNG, or WebP"
            );
        }

        String objectPath = "flashcard-sets/%s/images/%s.%s".formatted(
                flashcardSet.getId(), UUID.randomUUID(), extension);
        FileStorageService.StoredFile stored = fileStorageService.store(
                storageProperties.getLessonResourceBucket(),
                objectPath,
                contentType,
                content
        );
        return new FlashcardImageUploadResponse(stored.url());
    }

    /** Xác nhận bộ thẻ còn gắn với lesson flashcard legacy hoặc curriculum hiện tại. */
    private Course requireFlashcardCourse(FlashcardSet flashcardSet) {
        Lesson lesson = flashcardSet.getLesson();
        if (lesson != null) {
            Course course = lesson.getCourse();
            if (lesson.getType() != LessonType.FLASHCARD || course == null || course.getDeletedAt() != null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard lesson was not found");
            }
            return course;
        }

        UUID curriculumLessonId = flashcardSet.getCurriculumLessonId();
        CurriculumLesson curriculumLesson = curriculumLessonId == null
                ? null
                : curriculumLessonRepository.findById(curriculumLessonId).orElse(null);
        Course course = flashcardSet.getCourse();
        if (curriculumLesson == null
                || curriculumLesson.getType() != LessonType.FLASHCARD
                || course == null
                || course.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard lesson was not found");
        }
        return course;
    }

    /** Chỉ cho admin hoặc người tạo course thuộc SME/Trainer tải ảnh vào bộ thẻ. */
    private void requireEditAccess(UserAccount actor, Course course) {
        if (actor == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        String role = actor.getRole() == null ? "" : actor.getRole().trim();
        if ("ADMIN".equalsIgnoreCase(role)) {
            return;
        }
        if (("SME".equalsIgnoreCase(role) || "TRAINER".equalsIgnoreCase(role))
                && course.getCreator() != null
                && actor.getId().equals(course.getCreator().getId())) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "You are not allowed to edit this flashcard set");
    }

    /** Đọc tệp và từ chối dữ liệu rỗng hoặc vượt giới hạn ảnh cấu hình. */
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

    /** Nhận diện MIME từ nội dung thật để ngăn tệp giả mạo phần mở rộng. */
    private String detectContentType(byte[] content) {
        try {
            return tika.detect(content);
        }
        catch (Exception exception) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Flashcard image type could not be detected");
        }
    }
}
