package com.smartlearnly.backend.course.authoring.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.common.audit.AuditLogDetailResponse;
import com.smartlearnly.backend.common.audit.AuditLogQueryService;
import com.smartlearnly.backend.common.audit.AuditLogSummaryResponse;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.course.access.service.CourseAccessService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminCourseChangeHistoryControllerSecurityTest {
    private static final UUID COURSE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID AUDIT_LOG_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditLogQueryService auditLogQueryService;

    @MockitoBean
    private CourseAccessService courseAccessService;

    @Test
    void listShouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/courses/{courseId}/change-history", COURSE_ID))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(auditLogQueryService, courseAccessService);
    }

    @Test
    @WithMockUser(roles = "TRAINEE")
    void listShouldRejectTrainee() throws Exception {
        mockMvc.perform(get("/api/v1/admin/courses/{courseId}/change-history", COURSE_ID))
                .andExpect(status().isForbidden());
        verifyNoInteractions(auditLogQueryService);
    }

    @Test
    @WithMockUser(roles = "TRAINER")
    void listShouldRejectTrainer() throws Exception {
        mockMvc.perform(get("/api/v1/admin/courses/{courseId}/change-history", COURSE_ID))
                .andExpect(status().isForbidden());
        verifyNoInteractions(auditLogQueryService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listShouldRejectAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/courses/{courseId}/change-history", COURSE_ID))
                .andExpect(status().isForbidden());
        verifyNoInteractions(auditLogQueryService);
    }

    @Test
    @WithMockUser(roles = "TMO")
    void listShouldAllowTmoWhenCourseReadable() throws Exception {
        when(auditLogQueryService.listForCourse(
                        eq(COURSE_ID), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.<AuditLogSummaryResponse>of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/admin/courses/{courseId}/change-history", COURSE_ID))
                .andExpect(status().isOk());

        verify(courseAccessService).requireReadableCourse(COURSE_ID);
        verify(auditLogQueryService)
                .listForCourse(eq(COURSE_ID), any(), any(), any(), any(), any(), eq(0), eq(20));
    }

    @Test
    @WithMockUser(roles = "SME")
    void listShouldAllowSmeWhenCourseReadable() throws Exception {
        when(auditLogQueryService.listForCourse(
                        eq(COURSE_ID), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.<AuditLogSummaryResponse>of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/admin/courses/{courseId}/change-history", COURSE_ID))
                .andExpect(status().isOk());

        verify(courseAccessService).requireReadableCourse(COURSE_ID);
    }

    @Test
    @WithMockUser(roles = "SME")
    void listShouldReturnNotFoundWhenSmeNotAssigned() throws Exception {
        doThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Course was not found"))
                .when(courseAccessService)
                .requireReadableCourse(COURSE_ID);

        mockMvc.perform(get("/api/v1/admin/courses/{courseId}/change-history", COURSE_ID))
                .andExpect(status().isNotFound());
        verifyNoInteractions(auditLogQueryService);
    }

    @Test
    @WithMockUser(roles = "TRAINER")
    void detailShouldRejectTrainer() throws Exception {
        mockMvc.perform(get(
                        "/api/v1/admin/courses/{courseId}/change-history/{auditLogId}",
                        COURSE_ID,
                        AUDIT_LOG_ID))
                .andExpect(status().isForbidden());
        verifyNoInteractions(auditLogQueryService);
    }

    @Test
    @WithMockUser(roles = "TMO")
    void detailShouldAllowTmoWhenCourseReadable() throws Exception {
        when(auditLogQueryService.getForCourse(COURSE_ID, AUDIT_LOG_ID))
                .thenReturn(sampleDetail());

        mockMvc.perform(get(
                        "/api/v1/admin/courses/{courseId}/change-history/{auditLogId}",
                        COURSE_ID,
                        AUDIT_LOG_ID))
                .andExpect(status().isOk());

        verify(courseAccessService).requireReadableCourse(COURSE_ID);
        verify(auditLogQueryService).getForCourse(COURSE_ID, AUDIT_LOG_ID);
    }

    private static AuditLogDetailResponse sampleDetail() {
        return new AuditLogDetailResponse(
                AUDIT_LOG_ID,
                java.time.Instant.parse("2026-06-22T10:00:00Z"),
                null,
                null,
                "sme@example.com",
                "SME",
                "SECTION_CREATED",
                "CONTENT",
                "SUCCESS",
                "SECTION",
                UUID.randomUUID().toString(),
                "Section created",
                null,
                null,
                java.util.Map.of("courseId", COURSE_ID.toString()),
                null,
                null,
                null,
                null);
    }
}
