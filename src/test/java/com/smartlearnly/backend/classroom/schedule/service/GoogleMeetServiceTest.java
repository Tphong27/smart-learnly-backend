package com.smartlearnly.backend.classroom.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.smartlearnly.backend.admin.settings.service.SystemSettingsService;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.GoogleMeetSettings;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.GoogleOAuthSettings;
import com.smartlearnly.backend.classroom.schedule.config.GoogleMeetProperties;
import com.smartlearnly.backend.classroom.schedule.dto.MeetingUrlResponse;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class GoogleMeetServiceTest {

        private static final String TOKEN_BASE_URL = "https://oauth.example.test";
        private static final String MEET_BASE_URL = "https://meet.example.test";
        private static final String CLIENT_ID = "test-client-id.apps.googleusercontent.com";
        private static final String CLIENT_SECRET = "test-client-secret";
        private static final String REFRESH_TOKEN = "test-refresh-token";
        private static final String ACCESS_TOKEN = "test-access-token";
        private static final String MEETING_URL = "https://meet.google.com/abc-defg-hij";

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
                properties.setRefreshToken(REFRESH_TOKEN);
                properties.setTokenBaseUrl(TOKEN_BASE_URL);
                properties.setApiBaseUrl(MEET_BASE_URL);

                RestClient.Builder tokenBuilder = RestClient.builder().baseUrl(TOKEN_BASE_URL);
                RestClient.Builder meetBuilder = RestClient.builder().baseUrl(MEET_BASE_URL);

                tokenServer = MockRestServiceServer.bindTo(tokenBuilder).build();
                meetServer = MockRestServiceServer.bindTo(meetBuilder).build();

                service = new GoogleMeetService(
                                properties,
                                settingsService,
                                tokenBuilder.build(),
                                meetBuilder.build());
        }

        // createMeetingUrl(): UTCID restarts from UTCID01.
        // Both endpoints below are local MockRestServiceServer expectations.
        // No request is sent to Google OAuth or Google Meet.

        @Test
        void UTCID01_createMeetingUrl_exchangesRefreshTokenAndReturnsTrimmedMeetingUrl() {
                stubEnabledConfiguration(CLIENT_ID, CLIENT_SECRET, REFRESH_TOKEN);

                tokenServer.expect(requestTo(TOKEN_BASE_URL + "/token"))
                                .andExpect(method(HttpMethod.POST))
                                .andExpect(content().contentTypeCompatibleWith(
                                                MediaType.APPLICATION_FORM_URLENCODED))
                                .andExpect(content().formData(expectedTokenForm()))
                                .andRespond(withSuccess(
                                                """
                                                                {
                                                                  "access_token": "  test-access-token  ",
                                                                  "expires_in": 3600,
                                                                  "token_type": "Bearer"
                                                                }
                                                                """,
                                                MediaType.APPLICATION_JSON));

                meetServer.expect(requestTo(MEET_BASE_URL + "/v2/spaces"))
                                .andExpect(method(HttpMethod.POST))
                                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                                .andExpect(content().json("{}"))
                                .andRespond(withSuccess(
                                                """
                                                                {
                                                                  "name": "spaces/test-space",
                                                                  "meetingUri": "  https://meet.google.com/abc-defg-hij  ",
                                                                  "meetingCode": "abc-defg-hij"
                                                                }
                                                                """,
                                                MediaType.APPLICATION_JSON));

                MeetingUrlResponse result = service.createMeetingUrl();

                assertThat(result.meetingUrl()).isEqualTo(MEETING_URL);
                tokenServer.verify();
                meetServer.verify();
        }

        @Test
        void UTCID02_createMeetingUrl_rejectsDisabledIntegration() {
                when(settingsService.resolveGoogleMeetSettings())
                                .thenReturn(new GoogleMeetSettings(false, REFRESH_TOKEN));

                assertUnavailable(service::createMeetingUrl, "Google Meet link generation is disabled");

                verify(settingsService, never()).resolveGoogleSettings();
        }

        @Test
        void UTCID03_createMeetingUrl_rejectsNullGoogleOAuthSettings() {
                when(settingsService.resolveGoogleMeetSettings())
                                .thenReturn(new GoogleMeetSettings(true, REFRESH_TOKEN));
                when(settingsService.resolveGoogleSettings()).thenReturn(null);

                assertUnavailable(
                                service::createMeetingUrl,
                                "Google Meet link generation is not configured");
        }

        @Test
        void UTCID04_createMeetingUrl_rejectsBlankClientId() {
                stubEnabledConfiguration("   ", CLIENT_SECRET, REFRESH_TOKEN);

                assertUnavailable(
                                service::createMeetingUrl,
                                "Google Meet link generation is not configured");
        }

        @Test
        void UTCID05_createMeetingUrl_rejectsBlankClientSecret() {
                stubEnabledConfiguration(CLIENT_ID, "   ", REFRESH_TOKEN);

                assertUnavailable(
                                service::createMeetingUrl,
                                "Google Meet link generation is not configured");
        }

        @Test
        void UTCID06_createMeetingUrl_rejectsBlankRefreshToken() {
                stubEnabledConfiguration(CLIENT_ID, CLIENT_SECRET, "   ");

                assertUnavailable(
                                service::createMeetingUrl,
                                "Google Meet link generation is not configured");
        }

        @Test
        void UTCID07_createMeetingUrl_rejectsOAuthResponseWithNoBody() {
                stubEnabledConfiguration(CLIENT_ID, CLIENT_SECRET, REFRESH_TOKEN);
                tokenServer.expect(requestTo(TOKEN_BASE_URL + "/token"))
                                .andExpect(method(HttpMethod.POST))
                                .andRespond(withNoContent());

                assertUnavailable(
                                service::createMeetingUrl,
                                "Google OAuth did not return an access token");

                tokenServer.verify();
        }

        @Test
        void UTCID08_createMeetingUrl_rejectsOAuthResponseWithBlankAccessToken() {
                stubEnabledConfiguration(CLIENT_ID, CLIENT_SECRET, REFRESH_TOKEN);
                tokenServer.expect(requestTo(TOKEN_BASE_URL + "/token"))
                                .andExpect(method(HttpMethod.POST))
                                .andRespond(withSuccess(
                                                "{\"access_token\":\"   \"}",
                                                MediaType.APPLICATION_JSON));

                assertUnavailable(
                                service::createMeetingUrl,
                                "Google OAuth did not return an access token");

                tokenServer.verify();
        }

        @Test
        void UTCID09_createMeetingUrl_rejectsMeetResponseWithNoBody() {
                stubEnabledConfiguration(CLIENT_ID, CLIENT_SECRET, REFRESH_TOKEN);
                expectSuccessfulTokenResponse();
                meetServer.expect(requestTo(MEET_BASE_URL + "/v2/spaces"))
                                .andExpect(method(HttpMethod.POST))
                                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                                .andRespond(withNoContent());

                assertUnavailable(
                                service::createMeetingUrl,
                                "Google Meet did not return a meeting URL");

                tokenServer.verify();
                meetServer.verify();
        }

        @Test
        void UTCID10_createMeetingUrl_rejectsMeetResponseWithBlankMeetingUri() {
                stubEnabledConfiguration(CLIENT_ID, CLIENT_SECRET, REFRESH_TOKEN);
                expectSuccessfulTokenResponse();
                meetServer.expect(requestTo(MEET_BASE_URL + "/v2/spaces"))
                                .andExpect(method(HttpMethod.POST))
                                .andRespond(withSuccess(
                                                "{\"meetingUri\":\"   \"}",
                                                MediaType.APPLICATION_JSON));

                assertUnavailable(
                                service::createMeetingUrl,
                                "Google Meet did not return a meeting URL");

                tokenServer.verify();
                meetServer.verify();
        }

        @Test
        void UTCID11_createMeetingUrl_convertsOAuth401ToExternalServiceUnavailable() {
                stubEnabledConfiguration(CLIENT_ID, CLIENT_SECRET, REFRESH_TOKEN);
                tokenServer.expect(requestTo(TOKEN_BASE_URL + "/token"))
                                .andExpect(method(HttpMethod.POST))
                                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .body("{\"error\":\"invalid_grant\"}"));

                assertUnavailable(
                                service::createMeetingUrl,
                                "Google Meet rejected the request. Check the OAuth credentials and refresh token.");

                tokenServer.verify();
        }

        @Test
        void UTCID12_createMeetingUrl_convertsMeet503ToExternalServiceUnavailable() {
                stubEnabledConfiguration(CLIENT_ID, CLIENT_SECRET, REFRESH_TOKEN);
                expectSuccessfulTokenResponse();
                meetServer.expect(requestTo(MEET_BASE_URL + "/v2/spaces"))
                                .andExpect(method(HttpMethod.POST))
                                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

                assertUnavailable(
                                service::createMeetingUrl,
                                "Google Meet rejected the request. Check the OAuth credentials and refresh token.");

                tokenServer.verify();
                meetServer.verify();
        }

        @Test
        void UTCID13_createMeetingUrl_convertsOAuthSocketTimeoutToTemporaryUnavailable() {
                stubEnabledConfiguration(CLIENT_ID, CLIENT_SECRET, REFRESH_TOKEN);
                tokenServer.expect(requestTo(TOKEN_BASE_URL + "/token"))
                                .andExpect(method(HttpMethod.POST))
                                .andRespond(withException(new SocketTimeoutException(
                                                "simulated OAuth timeout")));

                assertUnavailable(
                                service::createMeetingUrl,
                                "Google Meet is temporarily unavailable");

                tokenServer.verify();
        }

        private void stubEnabledConfiguration(
                        String clientId,
                        String clientSecret,
                        String refreshToken) {
                when(settingsService.resolveGoogleMeetSettings())
                                .thenReturn(new GoogleMeetSettings(true, refreshToken));
                when(settingsService.resolveGoogleSettings())
                                .thenReturn(new GoogleOAuthSettings(
                                                clientId,
                                                clientSecret,
                                                "openid,profile,email"));
        }

        private MultiValueMap<String, String> expectedTokenForm() {
                MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
                form.add("client_id", CLIENT_ID);
                form.add("client_secret", CLIENT_SECRET);
                form.add("refresh_token", REFRESH_TOKEN);
                form.add("grant_type", "refresh_token");
                return form;
        }

        private void expectSuccessfulTokenResponse() {
                tokenServer.expect(requestTo(TOKEN_BASE_URL + "/token"))
                                .andExpect(method(HttpMethod.POST))
                                .andRespond(withSuccess(
                                                "{\"access_token\":\"test-access-token\"}",
                                                MediaType.APPLICATION_JSON));
        }

        private void assertUnavailable(Runnable invocation, String expectedMessage) {
                assertThatThrownBy(invocation::run)
                                .isInstanceOfSatisfying(BusinessException.class, error -> {
                                        assertThat(error.errorCode())
                                                        .isEqualTo(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
                                        assertThat(error.getMessage()).isEqualTo(expectedMessage);
                                });
        }
}