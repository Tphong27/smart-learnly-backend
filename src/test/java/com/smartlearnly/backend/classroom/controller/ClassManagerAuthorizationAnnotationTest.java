package com.smartlearnly.backend.classroom.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartlearnly.backend.classroom.admin.controller.AdminClassController;
import com.smartlearnly.backend.classroom.analytics.controller.ClassAnalyticsController;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;

class ClassManagerAuthorizationAnnotationTest {

    private static final String CLASS_MANAGER_AUTHORIZATION = "hasAnyRole('ADMIN', 'TMO')";

    @Test
    void classManagementEndpointsShouldAllowAdminAndTmo() {
        assertClassManagerAccess(AdminClassController.class, "listStatusOptions");
        assertClassManagerAccess(AdminClassController.class, "generateMeetingUrl");
        assertClassManagerAccess(AdminClassController.class, "listAdminClasses");
        assertClassManagerAccess(AdminClassController.class, "getAdminClass");
        assertClassManagerAccess(AdminClassController.class, "createClass");
        assertClassManagerAccess(AdminClassController.class, "updateClass");
        assertClassManagerAccess(AdminClassController.class, "cancelClass");
        assertClassManagerAccess(AdminClassController.class, "deleteClass");
    }

    @Test
    void classAnalyticsShouldAllowAdminAndTmo() {
        assertClassManagerAccess(
                ClassAnalyticsController.class,
                "getAdminOrTmoAnalytics");
    }

    private void assertClassManagerAccess(
            Class<?> controllerType,
            String methodName) {
        Method method = Arrays.stream(controllerType.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Method not found: "
                                + controllerType.getSimpleName()
                                + "."
                                + methodName));

        PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(
                method,
                PreAuthorize.class);

        assertThat(annotation)
                .as("%s.%s must declare @PreAuthorize",
                        controllerType.getSimpleName(),
                        methodName)
                .isNotNull();

        assertThat(annotation.value())
                .isEqualTo(CLASS_MANAGER_AUTHORIZATION);
    }
}
