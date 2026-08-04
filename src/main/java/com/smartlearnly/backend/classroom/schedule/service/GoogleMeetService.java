package com.smartlearnly.backend.classroom.schedule.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.GoogleMeetSettings;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.GoogleOAuthSettings;
import com.smartlearnly.backend.classroom.schedule.config.GoogleMeetProperties;
import com.smartlearnly.backend.classroom.schedule.dto.MeetingUrlResponse;
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
    // Khởi tạo client Google Meet từ cấu hình hệ thống có thể thay đổi khi chạy.
    public GoogleMeetService(GoogleMeetProperties properties, SystemSettingsService settingsService) {
        this(
                properties,
                settingsService,
                createRestClient(properties.getTokenBaseUrl(), properties.getTimeout()),
                createRestClient(properties.getApiBaseUrl(), properties.getTimeout()));
    }

    // Khởi tạo service với HTTP client tùy biến để phục vụ kiểm thử và cấu hình timeout.
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

    // Lấy access token OAuth rồi tạo Google Meet Space mới cho lớp học.
    public MeetingUrlResponse createMeetingUrl() {
        GoogleMeetSettings meetSettings = settingsService.resolveGoogleMeetSettings();
        GoogleOAuthSettings oauthSettings = requireConfiguration(meetSettings);

        try {
            String accessToken = requestAccessToken(oauthSettings, meetSettings);

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

    // Đổi refresh token thành access token Google trước khi gọi Meet API.
    private String requestAccessToken(GoogleOAuthSettings oauthSettings, GoogleMeetSettings meetSettings) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();

        form.add("client_id", oauthSettings.clientId());
        form.add("client_secret", oauthSettings.clientSecret());
        form.add("refresh_token", meetSettings.refreshToken());
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

    // Kiểm tra OAuth/Meet đã được cấu hình đủ trước khi cho phép tạo link.
    private GoogleOAuthSettings requireConfiguration(GoogleMeetSettings meetSettings) {
        if (meetSettings == null) {
            throw unavailable("Google Meet link generation is not configured");
        }

        if (!meetSettings.enabled()) {
            throw unavailable("Google Meet link generation is disabled");
        }

        GoogleOAuthSettings oauthSettings = settingsService.resolveGoogleSettings();

        boolean configured = oauthSettings != null
                && StringUtils.hasText(oauthSettings.clientId())
                && StringUtils.hasText(oauthSettings.clientSecret())
                && StringUtils.hasText(meetSettings.refreshToken());

        if (!configured) {
            throw unavailable("Google Meet link generation is not configured");
        }

        return oauthSettings;
    }

    // Tạo lỗi nghiệp vụ nhất quán khi tích hợp Google Meet chưa sẵn sàng.
    private BusinessException unavailable(String message) {
        return new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, message);
    }

    // Tạo HTTP client có base URL và timeout cho một Google API.
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
