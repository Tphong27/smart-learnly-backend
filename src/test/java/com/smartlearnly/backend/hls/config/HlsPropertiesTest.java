package com.smartlearnly.backend.hls.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class HlsPropertiesTest {

    private static final String STRONG_SECRET = "0123456789abcdef0123456789abcdef";

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

    @Test
    void validateSecurityConfigurationShouldAllowDefaultsWhenHlsIsDisabled() {
        HlsProperties properties = new HlsProperties();

        properties.validateSecurityConfiguration();
    }

    @Test
    void validateSecurityConfigurationShouldRejectDefaultTokenSecretWhenHlsIsEnabled() {
        HlsProperties properties = new HlsProperties();
        properties.setEnabled(true);

        assertThatThrownBy(properties::validateSecurityConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.hls.token-secret");
    }

    @Test
    void validateSecurityConfigurationShouldRejectShortTokenSecretWhenHlsIsEnabled() {
        HlsProperties properties = new HlsProperties();
        properties.setEnabled(true);
        properties.setTokenSecret("short-secret");

        assertThatThrownBy(properties::validateSecurityConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.hls.token-secret");
    }

    @Test
    void validateSecurityConfigurationShouldRequireCallbackSecretForGithubActions() {
        HlsProperties properties = new HlsProperties();
        properties.setEnabled(true);
        properties.setProcessingProvider("github-actions");
        properties.setTokenSecret(STRONG_SECRET);

        assertThatThrownBy(properties::validateSecurityConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.hls.callback-secret");
    }

    @Test
    void validateSecurityConfigurationShouldAcceptStrongSecretsForGithubActions() {
        HlsProperties properties = new HlsProperties();
        properties.setEnabled(true);
        properties.setProcessingProvider("github-actions");
        properties.setTokenSecret(STRONG_SECRET);
        properties.setCallbackSecret("fedcba9876543210fedcba9876543210");

        properties.validateSecurityConfiguration();
    }
}
