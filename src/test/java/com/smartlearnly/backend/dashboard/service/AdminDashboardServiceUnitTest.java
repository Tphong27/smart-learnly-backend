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
class AdminDashboardServiceUnitTest {
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
    void getOverviewShouldExposeUnavailableAndDisabledServices() {
        when(dashboardQueryRepository.countUsers())
                .thenReturn(new DashboardUsersResponse(3, 1, 1, 0, 0, 1));
        when(dashboardQueryRepository.isDatabaseUp()).thenReturn(false);
        when(systemSettingsService.resolveAssignmentAiSettings())
                .thenReturn(new AssignmentAiSettings(false, "gemini", "key", "gemini-test", "", 30));
        when(systemSettingsService.resolveEmailSettings())
                .thenReturn(new EmailSettings("https://email.test", "", null, null, null, "from@test.com"));
        when(systemSettingsService.resolveSePayBankDisplaySettings())
                .thenReturn(new SePayBankDisplaySettings("", "", ""));
        when(systemSettingsService.resolveSePayRuntimeSettings())
                .thenReturn(new SePayRuntimeSettings("", ""));
        when(systemSettingsService.resolveGoogleSettings())
                .thenReturn(new GoogleOAuthSettings("", "", "openid"));
        when(systemSettingsService.resolveGoogleMeetSettings())
                .thenReturn(new GoogleMeetSettings(true, ""));

        var response = service.getOverview();

        assertThat(response.systemHealth().database().status()).isEqualTo("DOWN");
        assertThat(response.systemHealth().services())
                .extracting(item -> item.status())
                .containsExactly("DISABLED", "NOT_CONFIGURED", "NOT_CONFIGURED", "NOT_CONFIGURED", "NOT_CONFIGURED");
        assertThat(response.configurationStatus().items())
                .extracting(item -> item.configured())
                .containsExactly(true, false, false, false, false);
        assertThat(response.accountStatus().banned()).isEqualTo(1);
    }
}
