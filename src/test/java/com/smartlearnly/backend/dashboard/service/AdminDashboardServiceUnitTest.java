package com.smartlearnly.backend.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.dashboard.dto.DashboardClassesResponse;
import com.smartlearnly.backend.dashboard.dto.DashboardContentResponse;
import com.smartlearnly.backend.dashboard.dto.DashboardCoursesResponse;
import com.smartlearnly.backend.dashboard.dto.DashboardQuestionsResponse;
import com.smartlearnly.backend.dashboard.dto.DashboardUsersResponse;
import com.smartlearnly.backend.dashboard.repository.AdminDashboardQueryRepository;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link AdminDashboardService} focusing on the most complex
 * function: {@code resolveRange} boundary handling. Pure Mockito/JUnit tests,
 * no Spring context involved.
 */
@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceUnitTest {

    @Mock
    private AdminDashboardQueryRepository dashboardQueryRepository;

    private AdminDashboardService service;

    @BeforeEach
    void setUp() {
        service = new AdminDashboardService(dashboardQueryRepository);
    }

    @Test
    void getOverviewShouldAllowRangeOfExactlyNinetyDays() {
        Instant to = Instant.parse("2026-07-01T00:00:00Z");
        Instant from = to.minus(90, ChronoUnit.DAYS);
        stubDashboardCounts();

        var response = service.getOverview(from, to);

        assertThat(response.range().from()).isEqualTo(from);
        assertThat(response.range().to()).isEqualTo(to);
        assertThat(Duration.between(from, to).toDays()).isEqualTo(90);
    }

    @Test
    void getOverviewShouldAllowZeroWidthRange() {
        Instant from = Instant.parse("2026-07-01T00:00:00Z");
        stubDashboardCounts();

        var response = service.getOverview(from, from);

        assertThat(response.range().from()).isEqualTo(from);
        assertThat(response.range().to()).isEqualTo(from);
        assertThat(Duration.between(response.range().from(), response.range().to())).isZero();
    }

    @Test
    void getOverviewShouldDefaultToLastThirtyDaysWhenNoRangeProvided() {
        stubDashboardCounts();

        var response = service.getOverview(null, null);

        assertThat(Duration.between(response.range().from(), response.range().to()))
                .isEqualTo(Duration.ofDays(30));
    }

    @Test
    void getOverviewShouldRejectRangeWithOnlyOneBound() {
        Instant from = Instant.parse("2026-06-01T00:00:00Z");

        assertThatThrownBy(() -> service.getOverview(from, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    void getOverviewShouldRejectRangeWhereFromIsAfterTo() {
        Instant from = Instant.parse("2026-07-02T00:00:00Z");
        Instant to = Instant.parse("2026-07-01T00:00:00Z");

        assertThatThrownBy(() -> service.getOverview(from, to))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    void getOverviewShouldRejectRangeLongerThanNinetyDays() {
        Instant to = Instant.parse("2026-07-01T00:00:00Z");
        Instant from = to.minus(91, ChronoUnit.DAYS);

        assertThatThrownBy(() -> service.getOverview(from, to))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    void getOverviewShouldPropagateRepositoryFailure() {
        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-07-01T00:00:00Z");
        when(dashboardQueryRepository.countUsers(any(), any()))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> service.getOverview(from, to))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
    }

    private void stubDashboardCounts() {
        when(dashboardQueryRepository.countUsers(any(), any()))
                .thenReturn(new DashboardUsersResponse(10, 8, 1, 1, 0, 2));
        when(dashboardQueryRepository.countCourses(any(), any()))
                .thenReturn(new DashboardCoursesResponse(5, 3, 1, 1, 1));
        when(dashboardQueryRepository.countClasses(any(), any()))
                .thenReturn(new DashboardClassesResponse(4, 1, 1, 1, 1, 1));
        when(dashboardQueryRepository.countContent(any(), any()))
                .thenReturn(new DashboardContentResponse(6, 12, 8, 3, 1, 2, 4));
        when(dashboardQueryRepository.countQuestions(any(), any()))
                .thenReturn(new DashboardQuestionsResponse(20, 15, 2, 1, 1, 1, 6, 5, 7, 13));
    }
}
