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
    void getOverviewShouldReturnHealthConfigurationAndAccountSnapshots() {
        when(dashboardQueryRepository.countUsers())
                .thenReturn(new DashboardUsersResponse(10, 6, 1, 1, 1, 1));
        when(dashboardQueryRepository.isDatabaseUp()).thenReturn(true);
        when(systemSettingsService.resolveAssignmentAiSettings())
                .thenReturn(new AssignmentAiSettings(true, "gemini", "key", "gemini-test", "", 30));
        when(systemSettingsService.resolveEmailSettings())
                .thenReturn(new EmailSettings("https://email.test", "key", null, null, null, "from@test.com"));
        when(systemSettingsService.resolveSePayBankDisplaySettings())
                .thenReturn(new SePayBankDisplaySettings("123", "Bank", "Smart Learnly"));
        when(systemSettingsService.resolveSePayRuntimeSettings())
                .thenReturn(new SePayRuntimeSettings("token", "secret"));
        when(systemSettingsService.resolveGoogleSettings())
                .thenReturn(new GoogleOAuthSettings("client", "secret", "openid"));
        when(systemSettingsService.resolveGoogleMeetSettings())
                .thenReturn(new GoogleMeetSettings(true, "refresh-token"));

        var response = service.getOverview();

        assertThat(response.generatedAt()).isNotNull();
        assertThat(response.systemHealth().backend().status()).isEqualTo("UP");
        assertThat(response.systemHealth().database().status()).isEqualTo("UP");
        assertThat(response.systemHealth().services()).hasSize(5);
        assertThat(response.configurationStatus().items()).allMatch(item -> item.configured() && item.enabled());
        assertThat(response.accountStatus().total()).isEqualTo(10);
        assertThat(response.accountStatus().locked()).isEqualTo(1);
    }
}
