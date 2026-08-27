package com.smartlearnly.backend.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.admin.settings.service.SystemSettingsService;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.AssignmentAiSettings;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.EmailSettings;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.GoogleMeetSettings;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.GoogleOAuthSettings;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.SePayBankDisplaySettings;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.SePayRuntimeSettings;
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
class AdminDashboardServiceUnitTest {
    @Mock
    private AdminDashboardQueryRepository dashboardQueryRepository;

    @Mock
    private SystemSettingsService systemSettingsService;

    @Mock
    private SystemSettingsService systemSettingsService;

    private AdminDashboardService service;

    @BeforeEach
    void setUp() {
        service = new AdminDashboardService(dashboardQueryRepository, systemSettingsService);
        service = new AdminDashboardService(dashboardQueryRepository, systemSettingsService);
    }

    @Test
    void getOverviewShouldReportConfiguredServicesAndAccountStatus() {
        stubHealthyOverview();

        var response = service.getOverview();

        assertThat(response.generatedAt()).isNotNull();
        assertThat(response.systemHealth().backend().status()).isEqualTo("UP");
        assertThat(response.systemHealth().database().status()).isEqualTo("UP");
        assertThat(response.systemHealth().services())
                .extracting(item -> item.id())
                .containsExactly("ai", "email", "sepay", "google_oauth", "google_meet");
        assertThat(response.systemHealth().services())
                .allMatch(item -> "CONFIGURED".equals(item.status()));
        assertThat(response.configurationStatus().items()).hasSize(5);
        assertThat(response.accountStatus().total()).isEqualTo(10);
        assertThat(response.accountStatus().active()).isEqualTo(8);
        assertThat(response.accountStatus().pendingVerify()).isEqualTo(1);
        assertThat(response.accountStatus().inactive()).isEqualTo(1);
        assertThat(response.accountStatus().locked()).isEqualTo(0);
        assertThat(response.accountStatus().banned()).isEqualTo(2);
    }

    @Test
    void getOverviewShouldMarkDatabaseDownWhenRepositoryReportsFailure() {
        stubHealthyOverview();
        when(dashboardQueryRepository.isDatabaseUp()).thenReturn(false);

        var response = service.getOverview();

        assertThat(response.systemHealth().database().status()).isEqualTo("DOWN");
        assertThat(response.systemHealth().backend().status()).isEqualTo("UP");
    }

    @Test
    void getOverviewShouldMarkAiDisabledWhenToggleIsOff() {
        stubHealthyOverview();
        when(systemSettingsService.resolveAssignmentAiSettings())
                .thenReturn(new AssignmentAiSettings(false, "gemini", "key", "gemini-flash", null, 30));

        var response = service.getOverview();

        var ai = response.systemHealth().services().stream()
                .filter(item -> "ai".equals(item.id()))
                .findFirst()
                .orElseThrow();
        assertThat(ai.status()).isEqualTo("DISABLED");
        assertThat(ai.detail()).contains("disabled");
    }

    @Test
    void getOverviewShouldPropagateRepositoryFailure() {
        when(dashboardQueryRepository.countUsers())
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> service.getOverview())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
    }

    private void stubHealthyOverview() {
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
