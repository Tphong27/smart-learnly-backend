package com.smartlearnly.backend.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    void successFactoryShouldPopulateTimestamp() {
        ApiResponse<String> response = ApiResponse.success("Profile loaded", "data");

        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Profile loaded");
        assertThat(response.data()).isEqualTo("data");
        assertThat(response.timestamp()).isNotNull();
    }
}
