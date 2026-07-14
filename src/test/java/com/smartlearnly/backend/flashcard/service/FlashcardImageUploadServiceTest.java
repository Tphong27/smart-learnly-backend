package com.smartlearnly.backend.flashcard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.file.config.StorageProperties;
import com.smartlearnly.backend.file.service.FileStorageService;
import com.smartlearnly.backend.flashcard.dto.FlashcardImageUploadResponse;
import com.smartlearnly.backend.flashcard.entity.FlashcardSet;
import com.smartlearnly.backend.flashcard.repository.FlashcardSetRepository;
import com.smartlearnly.backend.learning.lesson.entity.Lesson;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

@ExtendWith(MockitoExtension.class)
class FlashcardImageUploadServiceTest {
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
    );

    @Mock
    private FlashcardSetRepository flashcardSetRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private FileStorageService fileStorageService;

    private StorageProperties storageProperties;
    private FlashcardImageUploadService uploadService;

    @BeforeEach
    void setUp() {
        storageProperties = new StorageProperties();
        storageProperties.setLessonResourceBucket("lesson-resources");
        storageProperties.setQuestionImageMaxSize(DataSize.ofMegabytes(5));
        uploadService = new FlashcardImageUploadService(
                flashcardSetRepository,
                currentUserService,
                fileStorageService,
                storageProperties
        );
    }

    @Test
    void uploadShouldAllowCourseCreatorAndStoreDetectedPng() {
        UserAccount creator = user("SME");
        FlashcardSet set = flashcardSet(creator);
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(set.getId())).thenReturn(Optional.of(set));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(creator);
        when(fileStorageService.store(eq("lesson-resources"), any(), eq("image/png"), eq(PNG)))
                .thenAnswer(invocation -> new FileStorageService.StoredFile(
                        "https://cdn.test/" + invocation.getArgument(1),
                        invocation.getArgument(1),
                        "image.png",
                        "image/png",
                        PNG.length
                ));

        FlashcardImageUploadResponse response = uploadService.upload(
                set.getId(),
                new MockMultipartFile("file", "fake.txt", "text/plain", PNG)
        );

        assertThat(response.url()).startsWith("https://cdn.test/flashcard-sets/" + set.getId() + "/images/");
    }

    @Test
    void uploadShouldAllowAdminForAnySet() {
        UserAccount creator = user("SME");
        UserAccount admin = user("ADMIN");
        FlashcardSet set = flashcardSet(creator);
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(set.getId())).thenReturn(Optional.of(set));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(admin);
        when(fileStorageService.store(any(), any(), any(), any()))
                .thenReturn(new FileStorageService.StoredFile("https://cdn.test/image.png", "path", "image.png", "image/png", PNG.length));

        FlashcardImageUploadResponse response = uploadService.upload(
                set.getId(),
                new MockMultipartFile("file", "image.png", "image/png", PNG)
        );

        assertThat(response.url()).isEqualTo("https://cdn.test/image.png");
    }

    @Test
    void uploadShouldDenyUnrelatedAuthor() {
        UserAccount creator = user("SME");
        UserAccount otherTrainer = user("TRAINER");
        FlashcardSet set = flashcardSet(creator);
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(set.getId())).thenReturn(Optional.of(set));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(otherTrainer);

        assertThatThrownBy(() -> uploadService.upload(
                set.getId(),
                new MockMultipartFile("file", "image.png", "image/png", PNG)
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(fileStorageService, never()).store(any(), any(), any(), any());
    }

    @Test
    void uploadShouldRejectGifAndSpoofedContent() {
        UserAccount creator = user("TRAINER");
        FlashcardSet set = flashcardSet(creator);
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(set.getId())).thenReturn(Optional.of(set));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(creator);

        assertThatThrownBy(() -> uploadService.upload(
                set.getId(),
                new MockMultipartFile("file", "image.gif", "image/gif", "GIF89a".getBytes())
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE));
        verify(fileStorageService, never()).store(any(), any(), any(), any());
    }

    @Test
    void uploadShouldRejectOversizedFile() {
        UserAccount creator = user("TRAINER");
        FlashcardSet set = flashcardSet(creator);
        storageProperties.setQuestionImageMaxSize(DataSize.ofBytes(2));
        when(flashcardSetRepository.findByIdAndDeletedAtIsNull(set.getId())).thenReturn(Optional.of(set));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(creator);

        assertThatThrownBy(() -> uploadService.upload(
                set.getId(),
                new MockMultipartFile("file", "image.png", "image/png", PNG)
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE));
    }

    private UserAccount user(String role) {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail(role.toLowerCase() + "@smartlearnly.dev");
        user.setFullName(role);
        user.setRole(role);
        return user;
    }

    private FlashcardSet flashcardSet(UserAccount creator) {
        Course course = new Course();
        course.setId(UUID.randomUUID());
        course.setTitle("Course");
        course.setSlug("course");
        course.setCreator(creator);

        Lesson lesson = new Lesson();
        lesson.setId(UUID.randomUUID());
        lesson.setCourse(course);
        lesson.setTitle("Flashcards");
        lesson.setType(LessonType.FLASHCARD);

        FlashcardSet set = new FlashcardSet();
        set.setId(UUID.randomUUID());
        set.setCourse(course);
        set.setLesson(lesson);
        return set;
    }
}
