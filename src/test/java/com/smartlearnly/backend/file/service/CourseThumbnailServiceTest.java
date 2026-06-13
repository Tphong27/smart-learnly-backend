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
import com.smartlearnly.backend.file.dto.CourseThumbnailUploadResponse;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

@ExtendWith(MockitoExtension.class)
class CourseThumbnailServiceTest {
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
    );

    @Mock
    private FileStorageService fileStorageService;

    private StorageProperties storageProperties;
    private CourseThumbnailService courseThumbnailService;

    @BeforeEach
    void setUp() {
        storageProperties = new StorageProperties();
        storageProperties.setCourseThumbnailBucket("course-thumbnails");
        storageProperties.setCourseThumbnailMaxSize(DataSize.ofMegabytes(5));
        courseThumbnailService = new CourseThumbnailService(fileStorageService, storageProperties);
    }

    @Test
    void uploadShouldDetectActualPngAndIgnoreClientContentType() {
        when(fileStorageService.store(eq("course-thumbnails"), any(), eq("image/png"), eq(PNG)))
                .thenAnswer(invocation -> {
                    String path = invocation.getArgument(1);
                    return new FileStorageService.StoredFile(
                            "https://example.test/" + path,
                            path,
                            path.substring(path.lastIndexOf('/') + 1),
                            "image/png",
                            PNG.length
                    );
                });

        CourseThumbnailUploadResponse response = courseThumbnailService.upload(
                new MockMultipartFile("file", "fake.txt", "text/plain", PNG)
        );

        assertThat(response.contentType()).isEqualTo("image/png");
        assertThat(response.objectPath()).matches("\\d{4}/\\d{2}/[0-9a-f-]+\\.png");
        assertThat(response.size()).isEqualTo(PNG.length);
    }

    @Test
    void uploadShouldRejectUnsupportedActualContent() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fake.png",
                "image/png",
                "not an image".getBytes()
        );

        assertThatThrownBy(() -> courseThumbnailService.upload(file))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE));
        verify(fileStorageService, never()).store(any(), any(), any(), any());
    }

    @Test
    void uploadShouldRejectFileAboveConfiguredLimit() {
        storageProperties.setCourseThumbnailMaxSize(DataSize.ofBytes(2));
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", PNG);

        assertThatThrownBy(() -> courseThumbnailService.upload(file))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE));
    }

    @Test
    void uploadShouldRejectEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> courseThumbnailService.upload(file))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }
}
