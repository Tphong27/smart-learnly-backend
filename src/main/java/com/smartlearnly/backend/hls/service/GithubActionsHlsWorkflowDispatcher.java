package com.smartlearnly.backend.hls.service;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.hls.config.HlsGithubActionsProperties;
import java.net.URI;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
@Slf4j @Service
public class GithubActionsHlsWorkflowDispatcher implements HlsWorkflowDispatcher {
    private static final Pattern PART = Pattern.compile("^[A-Za-z0-9_.-]+$");
    private static final Pattern WORKFLOW = Pattern.compile("^[A-Za-z0-9_.-]+\\.ya?ml$");
    private final HlsGithubActionsProperties properties;
    private final RestClient client;
    public GithubActionsHlsWorkflowDispatcher(HlsGithubActionsProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.client = builder.build();
    }
    @Override public void dispatch(DispatchRequest request) {
        validate();
        URI uri = UriComponentsBuilder.fromUriString(properties.getApiBaseUrl())
                .pathSegment("repos", properties.getOwner(), properties.getRepository(), "actions",
                        "workflows", properties.getWorkflowFile(), "dispatches")
                .build().encode().toUri();
        Map<String, Object> body = Map.of("ref", properties.getRef(), "inputs", Map.of(
                "job_id", request.jobId().toString(), "lesson_id", request.lessonId().toString(),
                "source_key", request.sourceKey(), "output_prefix", request.outputPrefix(),
                "qualities", request.qualities(),
                "segment_duration", Integer.toString(request.segmentDuration()),
                "ffmpeg_preset", request.ffmpegPreset()));
        try {
            client.post().uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getToken())
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", properties.getApiVersion())
                    .header(HttpHeaders.USER_AGENT, "smart-learnly-backend")
                    .body(body).retrieve().toBodilessEntity();
            log.info("Dispatched HLS workflow job={} lesson={}", request.jobId(), request.lessonId());
        } catch (RestClientResponseException exception) {
            String requestId = exception.getResponseHeaders() == null
                    ? null
                    : exception.getResponseHeaders().getFirst("X-GitHub-Request-Id");
            log.warn(
                    "GitHub HLS dispatch rejected: job={}, lesson={}, status={}, requestId={}",
                    request.jobId(),
                    request.lessonId(),
                    exception.getStatusCode().value(),
                    requestId
            );
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "HLS processing workflow could not be started (GitHub status "
                            + exception.getStatusCode().value() + ")"
            );
        } catch (RestClientException exception) {
            log.warn(
                    "GitHub HLS dispatch failed before receiving a response: job={}, lesson={}",
                    request.jobId(),
                    request.lessonId(),
                    exception
            );
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "HLS processing workflow could not be started");
        }
    }
    @Override public boolean isConfigured() {
        try { validate(); return true; } catch (BusinessException exception) { return false; }
    }
    private void validate() {
        try {
            URI api = URI.create(properties.getApiBaseUrl());
            if (!"https".equalsIgnoreCase(api.getScheme()) || api.getHost() == null
                    || !matches(PART, properties.getOwner()) || !matches(PART, properties.getRepository())
                    || !matches(WORKFLOW, properties.getWorkflowFile())
                    || properties.getRef() == null || properties.getRef().isBlank()
                    || properties.getApiVersion() == null
                    || !properties.getApiVersion().matches("^\\d{4}-\\d{2}-\\d{2}$")
                    || properties.getToken() == null || properties.getToken().isBlank()) {
                throw configurationError();
            }
        } catch (IllegalArgumentException exception) { throw configurationError(); }
    }
    private boolean matches(Pattern pattern, String value) {
        return value != null && pattern.matcher(value).matches();
    }
    private BusinessException configurationError() {
        return new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                "GitHub Actions HLS processing is not configured");
    }
}
