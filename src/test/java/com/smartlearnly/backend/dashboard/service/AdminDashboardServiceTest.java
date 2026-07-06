package com.smartlearnly.backend.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.dashboard.dto.DashboardClassesResponse;
import com.smartlearnly.backend.dashboard.dto.DashboardContentResponse;
import com.smartlearnly.backend.dashboard.dto.DashboardCoursesResponse;
import com.smartlearnly.backend.dashboard.dto.DashboardQuestionBanksResponse;
import com.smartlearnly.backend.dashboard.dto.DashboardUsersResponse;
import com.smartlearnly.backend.dashboard.repository.AdminDashboardQueryRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {
    @Mock
    private AdminDashboardQueryRepository dashboardQueryRepository;

    private AdminDashboardService service;

    @BeforeEach
    void setUp() {
        service = new AdminDashboardService(dashboardQueryRepository);
    }

    @Test
    void getOverviewShouldUseDefaultThirtyDayRange() {
        stubDashboardCounts();

        var response = service.getOverview(null, null);

        assertThat(response.range().maxDays()).isEqualTo(90);
        assertThat(Duration.between(response.range().from(), response.range().to()).toDays()).isBetween(29L, 30L);
        assertThat(response.users().total()).isEqualTo(10);
        assertThat(response.recentActivities()).isEmpty();
    }

    @Test
    void getOverviewShouldRejectIncompleteOrInvalidDateRange() {
        Instant now = Instant.parse("2026-07-04T00:00:00Z");

        assertThatThrownBy(() -> service.getOverview(now, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.getOverview(null, now)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.getOverview(now, now.minus(1, ChronoUnit.DAYS))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.getOverview(now.minus(91, ChronoUnit.DAYS), now)).isInstanceOf(BusinessException.class);

        verify(dashboardQueryRepository, never()).countUsers(any(), any());
    }

    @Test
    void getOverviewShouldUseBoundedRangeForAllQueries() {
        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-07-01T00:00:00Z");
        stubDashboardCounts();

        service.getOverview(from, to);

        ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> toCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(dashboardQueryRepository).countUsers(fromCaptor.capture(), toCaptor.capture());
        verify(dashboardQueryRepository).countCourses(from, to);
        verify(dashboardQueryRepository).countClasses(from, to);
        verify(dashboardQueryRepository).countContent(from, to);
        verify(dashboardQueryRepository).countQuestionBanks(from, to);
        assertThat(fromCaptor.getValue()).isEqualTo(from);
        assertThat(toCaptor.getValue()).isEqualTo(to);
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
        when(dashboardQueryRepository.countQuestionBanks(any(), any()))
                .thenReturn(new DashboardQuestionBanksResponse(3, 1, 1, 1, 20, 15, 2, 1, 1, 1, 1, 6, 5, 7, 13));
    }
}
