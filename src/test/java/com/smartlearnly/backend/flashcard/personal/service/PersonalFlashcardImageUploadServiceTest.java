package com.smartlearnly.backend.flashcard.personal.service;

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
import com.smartlearnly.backend.file.config.StorageProperties;
import com.smartlearnly.backend.file.service.FileStorageService;
import com.smartlearnly.backend.flashcard.dto.FlashcardImageUploadResponse;
import com.smartlearnly.backend.flashcard.entity.FlashcardSet;
import com.smartlearnly.backend.flashcard.repository.FlashcardSetRepository;
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
class PersonalFlashcardImageUploadServiceTest {
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
    );

    @Mock
    private FlashcardSetRepository flashcardSetRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private FileStorageService fileStorageService;

    private PersonalFlashcardImageUploadService service;

    @BeforeEach
    void setUp() {
        StorageProperties storageProperties = new StorageProperties();
        storageProperties.setLessonResourceBucket("lesson-resources");
        storageProperties.setQuestionImageMaxSize(DataSize.ofMegabytes(5));
        service = new PersonalFlashcardImageUploadService(
                flashcardSetRepository,
                currentUserService,
                fileStorageService,
                storageProperties
        );
    }

    @Test
    void uploadShouldAuthorizeOwnedPersonalSetBeforeStoring() {
        UserAccount actor = user("TRAINEE");
        FlashcardSet set = personalSet(actor);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        when(flashcardSetRepository.findPersonalByIdAndOwnerId(set.getId(), actor.getId()))
                .thenReturn(Optional.of(set));
        when(fileStorageService.store(eq("lesson-resources"), any(), eq("image/png"), eq(PNG)))
                .thenAnswer(invocation -> new FileStorageService.StoredFile(
                        "https://cdn.test/" + invocation.getArgument(1),
                        invocation.getArgument(1),
                        "image.png",
                        "image/png",
                        PNG.length
                ));

        FlashcardImageUploadResponse response = service.upload(
                set.getId(), new MockMultipartFile("file", "image.png", "image/png", PNG)
        );

        assertThat(response.url()).startsWith("https://cdn.test/flashcard-sets/" + set.getId() + "/images/");
    }

    @Test
    void uploadShouldRejectIneligibleRoleBeforeStoring() {
        when(currentUserService.requireAuthenticatedUser()).thenReturn(user("ADMIN"));

        assertThatThrownBy(() -> service.upload(
                UUID.randomUUID(), new MockMultipartFile("file", "image.png", "image/png", PNG)
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(fileStorageService, never()).store(any(), any(), any(), any());
    }

    private UserAccount user(String role) {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setRole(role);
        return user;
    }

    private FlashcardSet personalSet(UserAccount owner) {
        FlashcardSet set = new FlashcardSet();
        set.setId(UUID.randomUUID());
        set.setCreatedBy(owner);
        set.setTitle("Personal set");
        set.setIsPublic(false);
        set.setIsOfficial(false);
        return set;
    }
}
