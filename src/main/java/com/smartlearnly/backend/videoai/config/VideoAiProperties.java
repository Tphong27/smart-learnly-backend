package com.smartlearnly.backend.videoai.config;

import java.nio.file.Path;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.video-ai")
public class VideoAiProperties {
    private boolean enabled = true;
    private String youtubeApiKey;
    private String youtubeApiBaseUrl = "https://www.googleapis.com/youtube/v3";
    private Duration youtubeApiTimeout = Duration.ofSeconds(20);
    private String pythonCommand = "python";
    private Path transcriptScriptPath = Path.of("scripts", "video-ai", "fetch-youtube-transcript.py");
    private Duration transcriptTimeout = Duration.ofSeconds(60);
    private int maxVideoDurationMinutes = 120;
    private int maxTranscriptCharacters = 100_000;
}
