package com.smartlearnly.backend.question.image;

import java.time.Duration;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.question-image-import")
public class QuestionImageImportProperties {
    private boolean enabled = true;
    private String provider = "gemini";
    private String apiKey;
    private String apiBaseUrl = "https://generativelanguage.googleapis.com/v1beta";
    private String model = "gemini-3.5-flash";
    private Duration timeout = Duration.ofSeconds(45);
    private DataSize maxFileSize = DataSize.ofMegabytes(10);
    private int maxFiles = 5;
    private List<String> allowedContentTypes = List.of("image/png", "image/jpeg", "image/webp");
}
