package com.smartlearnly.backend.dashboard.service;

import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.common.audit.AuditLogQueryService;
import com.smartlearnly.backend.common.audit.AuditLogSummaryResponse;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.dashboard.dto.AdminDashboardOverviewResponse;
import com.smartlearnly.backend.dashboard.dto.DashboardDateRangeResponse;
import com.smartlearnly.backend.dashboard.dto.DashboardRecentActivityResponse;
import com.smartlearnly.backend.dashboard.repository.AdminDashboardQueryRepository;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {
    private static final int DEFAULT_RANGE_DAYS = 30;
    private static final int MAX_RANGE_DAYS = 90;
    private static final int RECENT_ACTIVITY_LIMIT = 10;
    private static final Duration MAX_RANGE = Duration.ofDays(MAX_RANGE_DAYS);

    private final AdminDashboardQueryRepository dashboardQueryRepository;
    private final AuditLogQueryService auditLogQueryService;

    @Transactional(readOnly = true)
    public AdminDashboardOverviewResponse getOverview(Instant from, Instant to) {
        DateRange range = resolveRange(from, to);
        PageResponse<AuditLogSummaryResponse> auditPage = auditLogQueryService.list(
                null, null, null, null, null, null, null, null,
                range.from(), range.to(), 0, RECENT_ACTIVITY_LIMIT
        );

        return new AdminDashboardOverviewResponse(
                new DashboardDateRangeResponse(range.from(), range.to(), MAX_RANGE_DAYS),
                dashboardQueryRepository.countUsers(range.from(), range.to()),
                dashboardQueryRepository.countCourses(range.from(), range.to()),
                dashboardQueryRepository.countClasses(range.from(), range.to()),
                dashboardQueryRepository.countContent(),
                dashboardQueryRepository.countQuestionBanks(),
                auditPage.items().stream().map(DashboardRecentActivityResponse::from).toList(),
                Instant.now()
        );
    }

    private DateRange resolveRange(Instant from, Instant to) {
        if (from == null && to == null) {
            Instant resolvedTo = Instant.now();
            return new DateRange(resolvedTo.minus(Duration.ofDays(DEFAULT_RANGE_DAYS)), resolvedTo);
        }

        if (from == null || to == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Dashboard date range requires both from and to");
        }

        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Dashboard date from must not be after date to");
        }

        if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Dashboard date range must not exceed 90 days");
        }

        return new DateRange(from, to);
    }

    private record DateRange(Instant from, Instant to) {
    }
}
