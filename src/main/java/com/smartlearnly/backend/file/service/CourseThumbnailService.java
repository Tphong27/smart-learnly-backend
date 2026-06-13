package com.smartlearnly.backend.file.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.file.config.StorageProperties;
import com.smartlearnly.backend.file.dto.CourseThumbnailUploadResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class CourseThumbnailService {
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    private final FileStorageService fileStorageService;
    private final StorageProperties storageProperties;
    private final Clock clock = Clock.systemUTC();
    private final Tika tika = new Tika();

    public CourseThumbnailUploadResponse upload(MultipartFile file) {
        byte[] content = readAndValidateSize(file);
        String contentType = detectContentType(content);
        String extension = ALLOWED_TYPES.get(contentType);
        if (extension == null) {
            throw new BusinessException(
                    ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "Course thumbnail must be JPEG, PNG, or WebP"
            );
        }

        LocalDate today = LocalDate.now(clock);
        String objectPath = "%d/%02d/%s.%s".formatted(
                today.getYear(),
                today.getMonthValue(),
                UUID.randomUUID(),
                extension
        );
        FileStorageService.StoredFile stored = fileStorageService.store(
                storageProperties.getCourseThumbnailBucket(),
                objectPath,
                contentType,
                content
        );
        return new CourseThumbnailUploadResponse(
                stored.url(),
                stored.objectPath(),
                stored.fileName(),
                stored.contentType(),
                stored.size()
        );
    }

    private byte[] readAndValidateSize(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Course thumbnail file is required");
        }
        if (file.getSize() > storageProperties.getCourseThumbnailMaxSize().toBytes()) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE);
        }
        try {
            byte[] content = file.getBytes();
            if (content.length == 0) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Course thumbnail file is required");
            }
            return content;
        }
        catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Course thumbnail file could not be read");
        }
    }

    private String detectContentType(byte[] content) {
        try {
            return tika.detect(content);
        }
        catch (Exception exception) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Course thumbnail type could not be detected");
        }
    }
}
