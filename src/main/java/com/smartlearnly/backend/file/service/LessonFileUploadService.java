package com.smartlearnly.backend.file.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.file.config.StorageProperties;
import com.smartlearnly.backend.file.dto.LessonFileUploadResponse;
import java.io.IOException;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class LessonFileUploadService {
    private static final Set<String> MATERIAL_EXTENSIONS = Set.of(
            "pdf",
            "doc",
            "docx",
            "ppt",
            "pptx",
            "mp4",
            "webm",
            "mov"
    );
    private static final Set<String> RESOURCE_EXTENSIONS = Set.of(
            "pdf",
            "doc",
            "docx",
            "ppt",
            "pptx",
            "xls",
            "xlsx",
            "csv",
            "txt",
            "zip",
            "png",
            "jpg",
            "jpeg",
            "webp",
            "gif",
            "mp3",
            "mp4"
    );

    private final FileStorageService fileStorageService;
    private final StorageProperties storageProperties;
    private final Clock clock = Clock.systemUTC();
    private final Tika tika = new Tika();

    public LessonFileUploadResponse uploadMaterial(MultipartFile file) {
        return upload(
                file,
                storageProperties.getLessonMaterialBucket(),
                "lesson material",
                "Lesson material file is required",
                storageProperties.getLessonMaterialMaxSize().toBytes(),
                MATERIAL_EXTENSIONS
        );
    }

    public LessonFileUploadResponse uploadResource(MultipartFile file) {
        return upload(
                file,
                storageProperties.getLessonResourceBucket(),
                "lesson resource",
                "Lesson resource file is required",
                storageProperties.getLessonResourceMaxSize().toBytes(),
                RESOURCE_EXTENSIONS
        );
    }

    private LessonFileUploadResponse upload(
            MultipartFile file,
            String bucket,
            String folder,
            String requiredMessage,
            long maxSizeBytes,
            Set<String> allowedExtensions
    ) {
        byte[] content = readAndValidateSize(file, requiredMessage, maxSizeBytes);
        String originalFileName = sanitizeOriginalFileName(file.getOriginalFilename());
        String extension = extractExtension(originalFileName);
        if (!allowedExtensions.contains(extension)) {
            throw new BusinessException(
                    ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "Unsupported " + folder + " file type"
            );
        }

        String contentType = detectContentType(content, originalFileName, folder);
        LocalDate today = LocalDate.now(clock);
        String objectPath = "%d/%02d/%s-%s".formatted(
                today.getYear(),
                today.getMonthValue(),
                UUID.randomUUID(),
                toStorageFileName(originalFileName)
        );
        FileStorageService.StoredFile stored = fileStorageService.store(
                bucket,
                objectPath,
                contentType,
                content
        );
        return new LessonFileUploadResponse(
                stored.url(),
                stored.objectPath(),
                originalFileName,
                stored.size(),
                stored.contentType()
        );
    }

    private byte[] readAndValidateSize(MultipartFile file, String requiredMessage, long maxSizeBytes) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, requiredMessage);
        }
        if (file.getSize() > maxSizeBytes) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE);
        }
        try {
            byte[] content = file.getBytes();
            if (content.length == 0) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, requiredMessage);
            }
            return content;
        }
        catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Uploaded file could not be read");
        }
    }

    private String sanitizeOriginalFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Uploaded file name is required");
        }
        String normalized = originalFileName.trim().replace('\\', '/');
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (fileName.isBlank() || fileName.contains("..")) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Uploaded file name is invalid");
        }
        return fileName;
    }

    private String extractExtension(String fileName) {
        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex < 0 || extensionIndex == fileName.length() - 1) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Uploaded file must have an extension");
        }
        return fileName.substring(extensionIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String detectContentType(byte[] content, String fileName, String folder) {
        try {
            return tika.detect(content, fileName);
        }
        catch (Exception exception) {
            throw new BusinessException(
                    ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "Uploaded " + folder + " file type could not be detected"
            );
        }
    }

    private String toStorageFileName(String fileName) {
        String extension = extractExtension(fileName);
        String baseName = fileName.substring(0, fileName.length() - extension.length() - 1);
        String normalized = Normalizer.normalize(baseName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("(^[-.]+|[-.]+$)", "");
        if (normalized.isBlank()) {
            normalized = "file";
        }
        int maxBaseLength = 120 - extension.length() - 1;
        String storageBaseName = normalized.length() <= maxBaseLength
                ? normalized
                : normalized.substring(0, maxBaseLength);
        return storageBaseName + "." + extension;
    }
}
