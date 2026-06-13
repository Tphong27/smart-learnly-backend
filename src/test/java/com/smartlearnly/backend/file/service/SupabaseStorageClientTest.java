package com.smartlearnly.backend.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.file.config.StorageProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class SupabaseStorageClientTest {
    @Test
    void storeShouldReturnServiceUnavailableWhenStorageIsNotConfigured() {
        SupabaseStorageClient client = new SupabaseStorageClient(
                new StorageProperties(),
                RestClient.builder()
        );

        assertThatThrownBy(() -> client.store("course-thumbnails", "path/image.png", "image/png", new byte[]{1}))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
                    assertThat(exception.getMessage()).doesNotContainIgnoringCase("key");
                });
    }
}
