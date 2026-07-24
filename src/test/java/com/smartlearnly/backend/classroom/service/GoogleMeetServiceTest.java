package com.smartlearnly.backend.classroom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.smartlearnly.backend.admin.settings.service.SystemSettingsService;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.GoogleOAuthSettings;
import com.smartlearnly.backend.classroom.config.GoogleMeetProperties;
import com.smartlearnly.backend.classroom.dto.MeetingUrlResponse;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class GoogleMeetServiceTest {

    @Mock
    private SystemSettingsService settingsService;

    private GoogleMeetProperties properties;
    private MockRestServiceServer tokenServer;
    private MockRestServiceServer meetServer;
    private GoogleMeetService service;

    @BeforeEach
    void setUp() {
        properties = new GoogleMeetProperties();
        properties.setEnabled(true);
        properties.setRefreshToken(
                "fake-refresh-token");
        properties.setTokenBaseUrl(
                "https://oauth.example.test");
        properties.setApiBaseUrl(
                "https://meet.example.test");

        RestClient.Builder tokenBuilder = RestClient.builder()
                .baseUrl(
                        properties.getTokenBaseUrl());

        RestClient.Builder meetBuilder = RestClient.builder()
                .baseUrl(
                        properties.getApiBaseUrl());

        tokenServer = MockRestServiceServer
                .bindTo(tokenBuilder)
                .build();

        meetServer = MockRestServiceServer
                .bindTo(meetBuilder)
                .build();

        service = new GoogleMeetService(
                properties,
                settingsService,
                tokenBuilder.build(),
                meetBuilder.build());
    }

    @Test
    void createMeetingUrlShouldReturnProviderMeetingUri() {
        when(settingsService.resolveGoogleSettings())
                .thenReturn(
                        new GoogleOAuthSettings(
                                "fake-client-id",
                                "fake-client-secret",
                                "openid,profile,email"));

        tokenServer
                .expect(requestTo(
                        "https://oauth.example.test/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                                {
                                  "access_token": "fake-access-token",
                                  "expires_in": 3600,
                                  "token_type": "Bearer"
                                }
                                """,
                        MediaType.APPLICATION_JSON));

        meetServer
                .expect(requestTo(
                        "https://meet.example.test/v2/spaces"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer fake-access-token"))
                .andRespond(withSuccess(
                        """
                                {
                                  "name": "spaces/test-space",
                                  "meetingUri":
                                    "https://meet.google.com/abc-defg-hij",
                                  "meetingCode": "abc-defg-hij"
                                }
                                """,
                        MediaType.APPLICATION_JSON));

        MeetingUrlResponse response = service.createMeetingUrl();

        assertThat(response.meetingUrl())
                .isEqualTo(
                        "https://meet.google.com/abc-defg-hij");

        tokenServer.verify();
        meetServer.verify();
    }

    @Test
    void createMeetingUrlShouldRejectDisabledIntegration() {
        properties.setEnabled(false);

        assertThatThrownBy(() -> service.createMeetingUrl())
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(
                                    exception.errorCode())
                                    .isEqualTo(
                                            ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);

                            assertThat(
                                    exception.getMessage())
                                    .contains("disabled");
                        });
    }
}