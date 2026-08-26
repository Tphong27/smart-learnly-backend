package com.smartlearnly.backend.classroom.schedule.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.GoogleMeetSettings;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.GoogleOAuthSettings;
import com.smartlearnly.backend.classroom.schedule.config.GoogleMeetProperties;
import com.smartlearnly.backend.classroom.schedule.dto.MeetingUrlResponse;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import java.io.IOException;
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

    private static final String CREATE_SPACE_SCOPE = "https://www.googleapis.com/auth/meetings.space.created";
    private static final ObjectMapper GOOGLE_ERROR_JSON = new ObjectMapper();

    private final GoogleMeetProperties properties;
    private final SystemSettingsService settingsService;
    private final RestClient tokenClient;
    private final RestClient meetClient;

    /**
     * Khởi tạo client Google Meet từ cấu hình hệ thống có thể thay đổi khi chạy.
     */
    @Autowired
    public GoogleMeetService(GoogleMeetProperties properties, SystemSettingsService settingsService) {
        this(
                properties,
                settingsService,
                createRestClient(properties.getTokenBaseUrl(), properties.getTimeout()),
                createRestClient(properties.getApiBaseUrl(), properties.getTimeout()));
    }

    /**
     * Khởi tạo service với HTTP client tùy biến để phục vụ kiểm thử.
     */
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

    /**
     * Lấy access token OAuth rồi tạo Google Meet Space mới cho lớp học.
     */
    public MeetingUrlResponse createMeetingUrl() {
        GoogleMeetSettings meetSettings = settingsService.resolveGoogleMeetSettings();
        GoogleOAuthSettings oauthSettings = requireConfiguration(meetSettings);
        String accessToken = requestAccessToken(oauthSettings, meetSettings);

        try {
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
            throw meetApiFailure(exception);

        } catch (RestClientException exception) {
            log.warn("Google Meet request failed: errorType={}", exception.getClass().getSimpleName());
            throw unavailable("Google Meet is temporarily unavailable");
        }
    }

    /**
     * Đổi refresh token thành access token Google trước khi gọi Meet API.
     */
    private String requestAccessToken(GoogleOAuthSettings oauthSettings, GoogleMeetSettings meetSettings) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();

        form.add("client_id", oauthSettings.clientId());
        form.add("client_secret", oauthSettings.clientSecret());
        form.add("refresh_token", meetSettings.refreshToken());
        form.add("grant_type", "refresh_token");

        try {
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
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw oauthFailure(exception);
        } catch (RestClientException exception) {
            log.warn(
                    "Google OAuth token request failed: errorType={}",
                    exception.getClass().getSimpleName());
            throw unavailable("Google OAuth is temporarily unavailable");
        }
    }

    /**
     * Kiểm tra OAuth/Meet đã được cấu hình đủ trước khi cho phép tạo link.
     */
    private GoogleOAuthSettings requireConfiguration(GoogleMeetSettings meetSettings) {
        if (meetSettings == null) {
            throw unavailable("Google Meet link generation is not configured");
        }

        if (!meetSettings.enabled()) {
            throw unavailable("Google Meet link generation is disabled");
        }

        GoogleOAuthSettings sharedSettings = settingsService.resolveGoogleSettings();
        String clientId = preferredValue(
                sharedSettings == null ? null : sharedSettings.clientId(),
                properties.getClientId());
        String clientSecret = preferredValue(
                sharedSettings == null ? null : sharedSettings.clientSecret(),
                properties.getClientSecret());

        boolean configured = StringUtils.hasText(clientId)
                && StringUtils.hasText(clientSecret)
                && StringUtils.hasText(meetSettings.refreshToken());

        if (!configured) {
            throw unavailable("Google Meet link generation is not configured");
        }

        return new GoogleOAuthSettings(clientId.trim(), clientSecret.trim(), CREATE_SPACE_SCOPE);
    }

    /**
     * Ưu tiên credential riêng của Meet rồi mới dùng credential Google chung.
     */
    private String preferredValue(String dedicatedValue, String sharedValue) {
        return StringUtils.hasText(dedicatedValue) ? dedicatedValue : sharedValue;
    }

    /**
     * Ánh xạ lỗi OAuth refresh token thành thông báo có thể xử lý được.
     */
    private BusinessException oauthFailure(RestClientResponseException exception) {
        String providerCode = googleErrorCode(exception);
        log.warn(
                "Google OAuth token request rejected: status={} providerCode={}",
                exception.getStatusCode().value(),
                providerCode);

        return switch (providerCode) {
            case "invalid_grant" -> unavailable(
                    "Google Meet refresh token is expired or revoked. Reconnect Google Meet.");
            case "invalid_client" -> unavailable(
                    "Google Meet OAuth client ID or client secret is invalid.");
            default -> unavailable("Google OAuth rejected the refresh token request.");
        };
    }

    /**
     * Ánh xạ lỗi Meet API theo quyền, quota hoặc trạng thái dịch vụ.
     */
    private BusinessException meetApiFailure(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        String providerCode = googleErrorCode(exception);
        log.warn(
                "Google Meet API request rejected: status={} providerCode={}",
                status,
                providerCode);

        if (status == 401) {
            return unavailable("Google Meet rejected the access token. Reconnect Google Meet.");
        }
        if (status == 403) {
            return unavailable(
                    "Google Meet permission is missing. Re-authorize the meetings.space.created scope and enable Google Meet REST API.");
        }
        if (status == 429) {
            return unavailable("Google Meet quota has been exceeded. Try again later.");
        }
        if (status >= 500) {
            return unavailable("Google Meet is temporarily unavailable");
        }
        return unavailable("Google Meet rejected the create-space request.");
    }

    /**
     * Đọc mã lỗi Google an toàn mà không ghi response hoặc token vào log.
     */
    private String googleErrorCode(RestClientResponseException exception) {
        try {
            JsonNode root = GOOGLE_ERROR_JSON.readTree(exception.getResponseBodyAsString());
            JsonNode error = root == null ? null : root.path("error");
            if (error == null || error.isMissingNode() || error.isNull()) {
                return "unknown";
            }
            if (error.isTextual()) {
                return error.asText("unknown");
            }
            return error.path("status").asText("unknown");
        } catch (IOException | RuntimeException ignored) {
            return "unknown";
        }
    }

    /**
     * Tạo lỗi nghiệp vụ nhất quán khi tích hợp Google Meet chưa sẵn sàng.
     */
    private BusinessException unavailable(String message) {
        return new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, message);
    }

    /**
     * Tạo HTTP client có base URL và timeout cho một Google API.
     */
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
