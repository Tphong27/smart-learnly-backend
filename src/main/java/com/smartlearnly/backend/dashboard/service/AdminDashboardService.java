package com.smartlearnly.backend.dashboard.service;

import com.smartlearnly.backend.admin.settings.service.SystemSettingsService;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.AssignmentAiSettings;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.EmailSettings;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.GoogleMeetSettings;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.GoogleOAuthSettings;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.SePayBankDisplaySettings;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.SePayRuntimeSettings;
import com.smartlearnly.backend.dashboard.dto.AdminDashboardOverviewResponse;
import com.smartlearnly.backend.dashboard.dto.DashboardAccountStatusResponse;
import com.smartlearnly.backend.dashboard.dto.DashboardConfigurationItemResponse;
import com.smartlearnly.backend.dashboard.dto.DashboardConfigurationStatusResponse;
import com.smartlearnly.backend.dashboard.dto.DashboardHealthComponentResponse;
import com.smartlearnly.backend.dashboard.dto.DashboardServiceHealthResponse;
import com.smartlearnly.backend.dashboard.dto.DashboardSystemHealthResponse;
import com.smartlearnly.backend.dashboard.dto.DashboardUsersResponse;
import com.smartlearnly.backend.dashboard.repository.AdminDashboardQueryRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {
    private static final String STATUS_UP = "UP";
    private static final String STATUS_DOWN = "DOWN";
    private static final String STATUS_CONFIGURED = "CONFIGURED";
    private static final String STATUS_NOT_CONFIGURED = "NOT_CONFIGURED";
    private static final String STATUS_DISABLED = "DISABLED";

    private final AdminDashboardQueryRepository dashboardQueryRepository;
    private final SystemSettingsService systemSettingsService;

    @Transactional(readOnly = true)
    public AdminDashboardOverviewResponse getOverview() {
        DashboardUsersResponse users = dashboardQueryRepository.countUsers();
        boolean databaseUp = dashboardQueryRepository.isDatabaseUp();

        AssignmentAiSettings ai = systemSettingsService.resolveAssignmentAiSettings();
        EmailSettings email = systemSettingsService.resolveEmailSettings();
        SePayBankDisplaySettings sePayBank = systemSettingsService.resolveSePayBankDisplaySettings();
        SePayRuntimeSettings sePayRuntime = systemSettingsService.resolveSePayRuntimeSettings();
        GoogleOAuthSettings googleOAuth = systemSettingsService.resolveGoogleSettings();
        GoogleMeetSettings googleMeet = systemSettingsService.resolveGoogleMeetSettings();

        List<DashboardConfigurationItemResponse> configItems = buildConfigurationItems(
                ai, email, sePayBank, sePayRuntime, googleOAuth, googleMeet);
        List<DashboardServiceHealthResponse> services = buildServiceHealth(configItems, ai);

        return new AdminDashboardOverviewResponse(
                Instant.now(),
                new DashboardSystemHealthResponse(
                        new DashboardHealthComponentResponse(STATUS_UP),
                        new DashboardHealthComponentResponse(databaseUp ? STATUS_UP : STATUS_DOWN),
                        services
                ),
                new DashboardConfigurationStatusResponse(configItems),
                new DashboardAccountStatusResponse(
                        users.active(),
                        users.pendingVerify(),
                        users.inactive(),
                        users.locked(),
                        users.banned(),
                        users.total()
                )
        );
    }

    private List<DashboardConfigurationItemResponse> buildConfigurationItems(
            AssignmentAiSettings ai,
            EmailSettings email,
            SePayBankDisplaySettings sePayBank,
            SePayRuntimeSettings sePayRuntime,
            GoogleOAuthSettings googleOAuth,
            GoogleMeetSettings googleMeet
    ) {
        List<DashboardConfigurationItemResponse> items = new ArrayList<>();

        items.add(new DashboardConfigurationItemResponse(
                "ai",
                "AI generation",
                ai.isConfigured(),
                ai.enabled(),
                blankToNull(ai.provider()),
                blankToNull(ai.model())
        ));

        items.add(new DashboardConfigurationItemResponse(
                "email",
                "Email (Resend)",
                email.isConfigured(),
                email.isConfigured(),
                email.isConfigured() ? "resend" : null,
                null
        ));

        boolean sePayConfigured = sePayBank.isConfigured()
                && sePayRuntime.hasApiToken()
                && sePayRuntime.hasWebhookSecret();
        items.add(new DashboardConfigurationItemResponse(
                "sepay",
                "SePay payment",
                sePayConfigured,
                sePayConfigured,
                sePayConfigured ? "sepay" : null,
                null
        ));

        boolean googleOAuthConfigured = isNonBlank(googleOAuth.clientId())
                && isNonBlank(googleOAuth.clientSecret());
        items.add(new DashboardConfigurationItemResponse(
                "google_oauth",
                "Google OAuth login",
                googleOAuthConfigured,
                googleOAuthConfigured,
                googleOAuthConfigured ? "google" : null,
                null
        ));

        boolean meetConfigured = googleMeet.enabled() && isNonBlank(googleMeet.refreshToken());
        items.add(new DashboardConfigurationItemResponse(
                "google_meet",
                "Google Meet",
                meetConfigured,
                googleMeet.enabled(),
                meetConfigured ? "google" : null,
                null
        ));

        return items;
    }

    /**
     * Service health reflects configuration state only — no external provider pings
     * (avoids cost/rate-limit on every dashboard refresh).
     */
    private List<DashboardServiceHealthResponse> buildServiceHealth(
            List<DashboardConfigurationItemResponse> configItems,
            AssignmentAiSettings ai
    ) {
        List<DashboardServiceHealthResponse> services = new ArrayList<>();
        for (DashboardConfigurationItemResponse item : configItems) {
            String status;
            String detail = null;
            if ("ai".equals(item.id())) {
                if (!item.configured()) {
                    status = STATUS_NOT_CONFIGURED;
                    detail = "API key missing";
                } else if (!ai.enabled()) {
                    status = STATUS_DISABLED;
                    detail = "AI generation disabled in settings";
                } else {
                    status = STATUS_CONFIGURED;
                    detail = item.provider() != null && item.model() != null
                            ? item.provider() + " / " + item.model()
                            : null;
                }
            } else if (!item.configured()) {
                status = STATUS_NOT_CONFIGURED;
            } else if (!item.enabled()) {
                status = STATUS_DISABLED;
            } else {
                status = STATUS_CONFIGURED;
            }
            services.add(new DashboardServiceHealthResponse(item.id(), item.name(), status, detail));
        }
        return services;
    }

    private static boolean isNonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToNull(String value) {
        return isNonBlank(value) ? value : null;
    }
}
