package com.smartlearnly.backend.videoai.config;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class VideoAiJacksonConfigTest {

    @Test
    void configuredMapperCanPersistGeneratedArtifactTimestamps() {
        var mapper = new VideoAiJacksonConfig().videoAiObjectMapper();

        assertThatCode(() -> mapper.writeValueAsString(Instant.now()))
                .doesNotThrowAnyException();
    }
}
