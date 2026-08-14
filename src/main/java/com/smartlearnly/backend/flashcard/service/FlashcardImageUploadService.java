package com.smartlearnly.backend.flashcard.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.course.access.service.CourseAccessService;
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
    private final CourseAccessService courseAccessService;
    private final FileStorageService fileStorageService;
    private final StorageProperties storageProperties;
    private final Tika tika = new Tika();

    /** Tải ảnh cho master flashcard sau khi kiểm tra quyền chỉnh sửa course. */
    @Transactional(readOnly = true)
    public FlashcardImageUploadResponse upload(UUID setId, MultipartFile file) {
        FlashcardSet flashcardSet = flashcardSetRepository.findByIdAndDeletedAtIsNull(setId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard set was not found"));
        Course course = requireFlashcardCourse(flashcardSet);
        courseAccessService.requireUpdatableCourse(course.getId());

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

    private Course requireFlashcardCourse(FlashcardSet flashcardSet) {
        UUID curriculumLessonId = flashcardSet.getCurriculumLessonId();
        if (curriculumLessonId != null) {
            CurriculumLesson lesson = curriculumLessonRepository.findById(curriculumLessonId)
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.RESOURCE_NOT_FOUND,
                            "Flashcard lesson was not found"));
            Course course = flashcardSet.getCourse();
            UUID lessonCourseId = lesson.getSection() == null
                    || lesson.getSection().getCurriculumVersion() == null
                    ? null
                    : lesson.getSection().getCurriculumVersion().getCourseId();
            if (lesson.getType() != LessonType.FLASHCARD
                    || course == null
                    || course.getId() == null
                    || course.getDeletedAt() != null
                    || !course.getId().equals(lessonCourseId)) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard lesson was not found");
            }
            return course;
        }

        Lesson lesson = flashcardSet.getLesson();
        Course course = lesson == null ? flashcardSet.getCourse() : lesson.getCourse();
        if (lesson == null || lesson.getType() != LessonType.FLASHCARD || course == null || course.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Flashcard lesson was not found");
        }
        return course;
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
