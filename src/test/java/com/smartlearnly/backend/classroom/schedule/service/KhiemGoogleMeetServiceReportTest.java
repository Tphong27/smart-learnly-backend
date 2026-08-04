package com.smartlearnly.backend.classroom.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.smartlearnly.backend.admin.settings.service.SystemSettingsService;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.GoogleMeetSettings;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.GoogleOAuthSettings;
import com.smartlearnly.backend.classroom.schedule.config.GoogleMeetProperties;
import com.smartlearnly.backend.classroom.schedule.dto.MeetingUrlResponse;
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
class KhiemGoogleMeetServiceReportTest {

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
        properties.setRefreshToken("fake-refresh-token");
        properties.setTokenBaseUrl("https://oauth.example.test");
        properties.setApiBaseUrl("https://meet.example.test");
        RestClient.Builder tokenBuilder = RestClient.builder()
                .baseUrl(properties.getTokenBaseUrl());
        RestClient.Builder meetBuilder = RestClient.builder()
                .baseUrl(properties.getApiBaseUrl());
        tokenServer = MockRestServiceServer.bindTo(tokenBuilder).build();
        meetServer = MockRestServiceServer.bindTo(meetBuilder).build();
        service = new GoogleMeetService(
                properties,
                settingsService,
                tokenBuilder.build(),
                meetBuilder.build());
    }

    @Test
    void UTCID_KHIEM_BE_511_createMeetingUrl_exchangesTokenAndReturnsTrimmedUrl() {
        when(settingsService.resolveGoogleMeetSettings())
                .thenReturn(new GoogleMeetSettings(true, "fake-refresh-token"));
        when(settingsService.resolveGoogleSettings()).thenReturn(
                new GoogleOAuthSettings(
                        "fake-client-id",
                        "fake-client-secret",
                        "openid,profile,email"));
        tokenServer.expect(requestTo("https://oauth.example.test/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                        {"access_token":" fake-access-token "}
                        """,
                        MediaType.APPLICATION_JSON));
        meetServer.expect(requestTo("https://meet.example.test/v2/spaces"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer fake-access-token"))
                .andRespond(withSuccess(
                        """
                        {"meetingUri":" https://meet.google.com/abc-defg-hij "}
                        """,
                        MediaType.APPLICATION_JSON));

        MeetingUrlResponse result = service.createMeetingUrl();

        assertThat(result.meetingUrl())
                .isEqualTo("https://meet.google.com/abc-defg-hij");
        tokenServer.verify();
        meetServer.verify();
    }

    @Test
    void UTCID_KHIEM_BE_512_createMeetingUrl_rejectsDisabledIntegration() {
        when(settingsService.resolveGoogleMeetSettings())
                .thenReturn(new GoogleMeetSettings(false, "fake-refresh-token"));

        assertThatThrownBy(service::createMeetingUrl)
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.errorCode())
                            .isEqualTo(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
                    assertThat(error.getMessage()).contains("disabled");
                });
    }

    @Test
    void UTCID_KHIEM_BE_513_createMeetingUrl_rejectsIncompleteConfiguration() {
        when(settingsService.resolveGoogleMeetSettings())
                .thenReturn(new GoogleMeetSettings(true, " "));
        when(settingsService.resolveGoogleSettings()).thenReturn(
                new GoogleOAuthSettings(
                        "fake-client-id",
                        "fake-client-secret",
                        "openid,profile,email"));

        assertThatThrownBy(service::createMeetingUrl)
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.errorCode())
                            .isEqualTo(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
                    assertThat(error.getMessage()).contains("not configured");
                });
    }
}
