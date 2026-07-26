package com.smartlearnly.backend.classroom.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.GoogleOAuthSettings;
import com.smartlearnly.backend.classroom.config.GoogleMeetProperties;
import com.smartlearnly.backend.classroom.dto.MeetingUrlResponse;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Service
public class GoogleMeetService {

    private final GoogleMeetProperties properties;
    private final SystemSettingsService settingsService;
    private final RestClient tokenClient;
    private final RestClient meetClient;

    @Autowired
    public GoogleMeetService(GoogleMeetProperties properties, SystemSettingsService settingsService) {
        this(
                properties,
                settingsService,
                createRestClient(properties.getTokenBaseUrl(), properties.getTimeout()),
                createRestClient(properties.getApiBaseUrl(), properties.getTimeout()));
    }

    GoogleMeetService(
            GoogleMeetProperties properties,
            SystemSettingsService settingsService,
            RestClient tokenClient,
            RestClient meetClient) {
        this.properties = properties;
        this.settingsService = settingsService;
        this.tokenClient = tokenClient;
        this.meetClient = meetClient;
    }

    public MeetingUrlResponse createMeetingUrl() {
        GoogleOAuthSettings oauthSettings = requireConfiguration();

        try {
            String accessToken = requestAccessToken(oauthSettings);

            GoogleMeetSpaceResponse space = meetClient
                    .post()
                    .uri("/v2/spaces")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of())
                    .retrieve()
                    .body(GoogleMeetSpaceResponse.class);

            if (space == null || !StringUtils.hasText(space.meetingUri())) {
                throw unavailable("Google Meet did not return a meeting URL");
            }

            return new MeetingUrlResponse(space.meetingUri().trim());

        } catch (BusinessException exception) {
            throw exception;

        } catch (RestClientResponseException exception) {
            log.warn("Google Meet request failed: status={}", exception.getStatusCode().value());
            throw unavailable("Google Meet rejected the request. " + "Check the OAuth credentials and refresh token.");

        } catch (RestClientException exception) {
            log.warn("Google Meet request failed: errorType={}", exception.getClass().getSimpleName());
            throw unavailable("Google Meet is temporarily unavailable");
        }
    }

    private String requestAccessToken(GoogleOAuthSettings oauthSettings) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();

        form.add("client_id", oauthSettings.clientId());
        form.add("client_secret", oauthSettings.clientSecret());
        form.add("refresh_token", properties.getRefreshToken());
        form.add("grant_type", "refresh_token");

        GoogleAccessTokenResponse response = tokenClient
                .post()
                .uri("/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(GoogleAccessTokenResponse.class);

        if (response == null || !StringUtils.hasText(response.accessToken())) {
            throw unavailable("Google OAuth did not return an access token");
        }

        return response.accessToken().trim();
    }

    private GoogleOAuthSettings requireConfiguration() {
        if (!properties.isEnabled()) {
            throw unavailable("Google Meet link generation is disabled");
        }

        GoogleOAuthSettings oauthSettings = settingsService.resolveGoogleSettings();

        boolean configured = oauthSettings != null
                && StringUtils.hasText(oauthSettings.clientId())
                && StringUtils.hasText(oauthSettings.clientSecret())
                && StringUtils.hasText(properties.getRefreshToken());

        if (!configured) {
            throw unavailable("Google Meet link generation is not configured");
        }

        return oauthSettings;
    }

    private BusinessException unavailable(String message) {
        return new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, message);
    }

    private static RestClient createRestClient(String baseUrl, Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);

        return RestClient
                .builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GoogleAccessTokenResponse(@JsonProperty("access_token") String accessToken) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GoogleMeetSpaceResponse(String meetingUri) {
    }
}