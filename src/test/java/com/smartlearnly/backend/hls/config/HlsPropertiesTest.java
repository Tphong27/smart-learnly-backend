package com.smartlearnly.backend.hls.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HlsPropertiesTest {

    @Test
    void normalizedQualitiesShouldDefaultToTwoStepLadder() {
        HlsProperties properties = new HlsProperties();

        assertThat(properties.normalizedQualities()).isEqualTo("480p,720p");
    }

    @Test
    void normalizedQualitiesShouldKeepAllowedUniqueValuesInOrder() {
        HlsProperties properties = new HlsProperties();
        properties.setQualities("720p, 480p,unknown,720p,1080p");

        assertThat(properties.normalizedQualities()).isEqualTo("720p,480p,1080p");
    }

    @Test
    void normalizedFfmpegPresetShouldDefaultToVeryFastForInvalidValue() {
        HlsProperties properties = new HlsProperties();
        properties.setFfmpegPreset("invalid");

        assertThat(properties.normalizedFfmpegPreset()).isEqualTo("veryfast");
    }

    @Test
    void normalizedSegmentDurationShouldStayWithinHlsScriptBounds() {
        HlsProperties properties = new HlsProperties();

        properties.setSegmentDuration(1);
        assertThat(properties.normalizedSegmentDuration()).isEqualTo(2);

        properties.setSegmentDuration(40);
        assertThat(properties.normalizedSegmentDuration()).isEqualTo(30);
    }
}
