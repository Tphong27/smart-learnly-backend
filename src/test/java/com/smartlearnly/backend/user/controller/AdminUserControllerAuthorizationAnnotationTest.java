package com.smartlearnly.backend.user.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;

class AdminUserControllerAuthorizationAnnotationTest {
    @Test
    void adminUserLookupShouldAllowAdminAndTmoOnly() {
        PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(
                AdminUserController.class,
                PreAuthorize.class
        );

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasAnyRole('ADMIN', 'TMO')");
    }

    @Test
    void getShouldMapUserIdRoute() throws Exception {
        Method method = AdminUserController.class.getMethod("get", UUID.class);
        GetMapping annotation = method.getAnnotation(GetMapping.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactly("/{userId}");
    }

    @Test
    void updateShouldMapUserIdRoute() throws Exception {
        Method method = AdminUserController.class.getMethod(
                "update",
                UUID.class,
                com.smartlearnly.backend.user.dto.UpdateAdminUserRequest.class
        );
        PatchMapping annotation = method.getAnnotation(PatchMapping.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactly("/{userId}");
    }

    @Test
    void deleteShouldMapUserIdRoute() throws Exception {
        Method method = AdminUserController.class.getMethod("delete", UUID.class);
        DeleteMapping annotation = method.getAnnotation(DeleteMapping.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactly("/{userId}");
    }
}
