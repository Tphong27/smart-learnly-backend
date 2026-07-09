package com.smartlearnly.backend.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.file.config.StorageProperties;
import com.smartlearnly.backend.file.dto.LessonFileUploadResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

@ExtendWith(MockitoExtension.class)
class LessonFileUploadServiceTest {
    private static final byte[] CONTENT = "lesson notes".getBytes();

    @Mock
    private FileStorageService fileStorageService;

    private StorageProperties storageProperties;
    private LessonFileUploadService lessonFileUploadService;

    @BeforeEach
    void setUp() {
        storageProperties = new StorageProperties();
        storageProperties.setLessonMaterialBucket("lesson-materials");
        storageProperties.setLessonMaterialMaxSize(DataSize.ofMegabytes(50));
        storageProperties.setLessonResourceBucket("lesson-resources");
        storageProperties.setLessonResourceMaxSize(DataSize.ofMegabytes(20));
        lessonFileUploadService = new LessonFileUploadService(fileStorageService, storageProperties);
    }

    @Test
    void uploadMaterialShouldStoreAllowedDocumentAndReturnFrontendContract() {
        when(fileStorageService.store(eq("lesson-materials"), any(), any(), eq(CONTENT)))
                .thenAnswer(invocation -> {
                    String path = invocation.getArgument(1);
                    String contentType = invocation.getArgument(2);
                    return new FileStorageService.StoredFile(
                            "https://example.test/" + path,
                            path,
                            path.substring(path.lastIndexOf('/') + 1),
                            contentType,
                            CONTENT.length
                    );
                });

        LessonFileUploadResponse response = lessonFileUploadService.uploadMaterial(
                new MockMultipartFile("file", "Bai giang 1.pdf", "application/pdf", CONTENT)
        );

        assertThat(response.url()).startsWith("https://example.test/");
        assertThat(response.objectPath()).matches("\\d{4}/\\d{2}/[0-9a-f-]+-bai-giang-1\\.pdf");
        assertThat(response.fileName()).isEqualTo("Bai giang 1.pdf");
        assertThat(response.fileSize()).isEqualTo(CONTENT.length);
        assertThat(response.contentType()).isNotBlank();
    }

    @Test
    void uploadMaterialShouldRejectUnsupportedExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resources.zip",
                "application/zip",
                CONTENT
        );

        assertThatThrownBy(() -> lessonFileUploadService.uploadMaterial(file))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE));
        verify(fileStorageService, never()).store(any(), any(), any(), any());
    }

    @Test
    void uploadResourceShouldRejectFileAboveConfiguredLimit() {
        storageProperties.setLessonResourceMaxSize(DataSize.ofBytes(2));
        MockMultipartFile file = new MockMultipartFile("file", "resource.pdf", "application/pdf", CONTENT);

        assertThatThrownBy(() -> lessonFileUploadService.uploadResource(file))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE));
        verify(fileStorageService, never()).store(any(), any(), any(), any());
    }

    @Test
    void uploadMaterialShouldAcceptAudioFiles() {
        when(fileStorageService.store(eq("lesson-materials"), any(), any(), eq(CONTENT)))
                .thenAnswer(invocation -> storedFile(invocation.getArgument(1), invocation.getArgument(2)));

        LessonFileUploadResponse response = lessonFileUploadService.uploadMaterial(
                new MockMultipartFile("file", "listening.m4a", "audio/mp4", CONTENT)
        );

        assertThat(response.objectPath()).matches("\\d{4}/\\d{2}/[0-9a-f-]+-listening\\.m4a");
        assertThat(response.fileName()).isEqualTo("listening.m4a");
    }

    @Test
    void uploadResourceShouldAcceptAudioFiles() {
        when(fileStorageService.store(eq("lesson-resources"), any(), any(), eq(CONTENT)))
                .thenAnswer(invocation -> storedFile(invocation.getArgument(1), invocation.getArgument(2)));

        LessonFileUploadResponse response = lessonFileUploadService.uploadResource(
                new MockMultipartFile("file", "listening.wav", "audio/wav", CONTENT)
        );

        assertThat(response.objectPath()).matches("\\d{4}/\\d{2}/[0-9a-f-]+-listening\\.wav");
        assertThat(response.fileName()).isEqualTo("listening.wav");
    }

    private FileStorageService.StoredFile storedFile(String path, String contentType) {
        return new FileStorageService.StoredFile(
                "https://example.test/" + path,
                path,
                path.substring(path.lastIndexOf('/') + 1),
                contentType,
                CONTENT.length
        );
    }
}
