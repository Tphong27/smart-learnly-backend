package com.smartlearnly.backend.videoai.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class VideoAiContentTest {

    @Test
    void initializesDefaultsAndLifecycleTimestamps() {
        VideoAiContent content = new VideoAiContent();
        content.setStatus(null);
        content.setKeyPointsJson(null);

        content.prePersist();

        assertThat(content.getCreatedAt()).isNotNull();
        assertThat(content.getUpdatedAt()).isNotNull();
        assertThat(content.getUpdatedAt()).isAfterOrEqualTo(content.getCreatedAt());
        assertThat(content.getStatus()).isEqualTo("draft");
        assertThat(content.getKeyPointsJson()).isEqualTo("[]");
        assertThat(content.getRevision()).isZero();
        assertThat(content.getSegments()).isEmpty();
    }

    @Test
    void preservesExplicitValuesAndRefreshesOnlyUpdatedTimestamp() {
        Instant originalCreatedAt = Instant.parse("2025-01-01T00:00:00Z");
        VideoAiContent content = new VideoAiContent();
        content.setCreatedAt(originalCreatedAt);
        content.setStatus("published");
        content.setKeyPointsJson("[\"point\"]");

        content.prePersist();

        assertThat(content.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(content.getStatus()).isEqualTo("published");
        assertThat(content.getKeyPointsJson()).isEqualTo("[\"point\"]");

        Instant persistedUpdatedAt = content.getUpdatedAt();
        content.preUpdate();

        assertThat(content.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(content.getUpdatedAt()).isAfterOrEqualTo(persistedUpdatedAt);
    }
}
