package com.smartlearnly.backend.flashcard.personal.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.file.config.StorageProperties;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PersonalFlashcardImageUrlPolicy {
    private final StorageProperties storageProperties;

    /** Chỉ chấp nhận URL ảnh mới thuộc đúng thư mục R2 của bộ flashcard hiện tại. */
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

    /** Dựng tiền tố URL công khai của thư mục ảnh thuộc bộ flashcard. */
    private String expectedPrefix(UUID setId) {
        String imagePath = "flashcard-sets/" + setId + "/images/";
        return normalizeBase(firstConfigured(
                storageProperties.getR2LessonResourcePublicUrl(),
                storageProperties.getR2PublicUrl()
        )) + "/" + imagePath;
    }

    /** Ưu tiên public URL riêng của bucket trước public URL R2 dùng chung. */
    private String firstConfigured(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
    }

    /** Chuẩn hóa public URL R2 và báo lỗi khi chưa cấu hình. */
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
