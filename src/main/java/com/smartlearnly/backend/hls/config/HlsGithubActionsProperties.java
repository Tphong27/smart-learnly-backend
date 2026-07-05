package com.smartlearnly.backend.hls.config;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
@Getter @Setter @Component
@ConfigurationProperties(prefix = "app.hls.github-actions")
public class HlsGithubActionsProperties {
    private String apiBaseUrl = "https://api.github.com";
    private String apiVersion = "2026-03-10";
    private String owner;
    private String repository;
    private String workflowFile = "hls-transcode.yml";
    private String ref = "main";
    private String token;
}
