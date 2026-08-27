package com.smartlearnly.backend.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.admin.settings.service.SystemSettingsService;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.AssignmentAiSettings;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.EmailSettings;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.GoogleMeetSettings;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.GoogleOAuthSettings;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.SePayBankDisplaySettings;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.SePayRuntimeSettings;
import com.smartlearnly.backend.dashboard.dto.DashboardUsersResponse;
import com.smartlearnly.backend.dashboard.repository.AdminDashboardQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock
    private AdminDashboardQueryRepository dashboardQueryRepository;

    @Mock
    private SystemSettingsService systemSettingsService;

    private AdminDashboardService service;

    @BeforeEach
    void setUp() {
        service = new AdminDashboardService(dashboardQueryRepository, systemSettingsService);
    }

    @Test
    void getOverviewShouldReturnAccountAndConfigurationSnapshot() {
        stubDashboard();

        var response = service.getOverview();

        assertThat(response.accountStatus().total()).isEqualTo(10);
        assertThat(response.accountStatus().active()).isEqualTo(8);
        assertThat(response.configurationStatus().items())
                .extracting(item -> item.id())
                .containsExactly("ai", "email", "sepay", "google_oauth", "google_meet");
        assertThat(response.configurationStatus().items())
                .allMatch(item -> item.configured());
        assertThat(response.systemHealth().backend().status()).isEqualTo("UP");
        assertThat(response.systemHealth().database().status()).isEqualTo("UP");
    }

    @Test
    void getOverviewShouldMarkMissingApiKeyAsNotConfigured() {
        stubDashboard();
        when(systemSettingsService.resolveAssignmentAiSettings())
                .thenReturn(new AssignmentAiSettings(true, "gemini", " ", "gemini-flash", null, 30));
        when(systemSettingsService.resolveEmailSettings())
                .thenReturn(new EmailSettings(null, null, null, null, null, null));

        var response = service.getOverview();

        var ai = response.configurationStatus().items().stream()
                .filter(item -> "ai".equals(item.id()))
                .findFirst()
                .orElseThrow();
        var email = response.configurationStatus().items().stream()
                .filter(item -> "email".equals(item.id()))
                .findFirst()
                .orElseThrow();
        assertThat(ai.configured()).isFalse();
        assertThat(email.configured()).isFalse();
        assertThat(response.systemHealth().services().stream()
                .filter(item -> "ai".equals(item.id()))
                .findFirst()
                .orElseThrow()
                .status()).isEqualTo("NOT_CONFIGURED");
    }

    private void stubDashboard() {
        when(dashboardQueryRepository.countUsers())
                .thenReturn(new DashboardUsersResponse(10, 8, 1, 1, 0, 2));
        when(dashboardQueryRepository.isDatabaseUp()).thenReturn(true);
        when(systemSettingsService.resolveAssignmentAiSettings())
                .thenReturn(new AssignmentAiSettings(true, "gemini", "key", "gemini-flash", null, 30));
        when(systemSettingsService.resolveEmailSettings())
                .thenReturn(new EmailSettings(null, "rk", null, null, null, null));
        when(systemSettingsService.resolveSePayBankDisplaySettings())
                .thenReturn(new SePayBankDisplaySettings("1", "VCB", "Name"));
        when(systemSettingsService.resolveSePayRuntimeSettings())
                .thenReturn(new SePayRuntimeSettings("tok", "sec"));
        when(systemSettingsService.resolveGoogleSettings())
                .thenReturn(new GoogleOAuthSettings("cid", "csec", "openid"));
        when(systemSettingsService.resolveGoogleMeetSettings())
                .thenReturn(new GoogleMeetSettings(true, "rt"));
    }
}
