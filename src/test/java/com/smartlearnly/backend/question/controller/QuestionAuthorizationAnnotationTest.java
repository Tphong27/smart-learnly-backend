package com.smartlearnly.backend.question.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartlearnly.backend.question.ai.controller.CourseAiQuestionDraftController;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;

class QuestionAuthorizationAnnotationTest {

    @Test
    void courseQuestionReadBlockAllowsCourseAuthoringRoles() {
        assertThat(preAuthorizeValue(CourseQuestionController.class))
                .isEqualTo("hasAnyRole('ADMIN', 'SME', 'TMO', 'TRAINER')");
    }

    @Test
    void courseQuestionMutationsRemainAdminAndSmeOnly() {
        assertThat(preAuthorizeValue(method(CourseQuestionController.class, "create")))
                .isEqualTo("hasAnyRole('ADMIN', 'SME')");
        assertThat(preAuthorizeValue(method(CourseQuestionController.class, "update")))
                .isEqualTo("hasAnyRole('ADMIN', 'SME')");
        assertThat(preAuthorizeValue(method(CourseQuestionController.class, "archive")))
                .isEqualTo("hasAnyRole('ADMIN', 'SME')");
        assertThat(preAuthorizeValue(method(CourseQuestionController.class, "importBatch")))
                .isEqualTo("hasAnyRole('ADMIN', 'SME')");
    }

    @Test
    void questionMediaControllersRemainAdminAndSmeOnly() {
        assertThat(preAuthorizeValue(QuestionMediaController.class))
                .isEqualTo("hasAnyRole('ADMIN', 'SME')");
        assertThat(preAuthorizeValue(QuestionAnswerMediaController.class))
                .isEqualTo("hasAnyRole('ADMIN', 'SME')");
        assertThat(preAuthorizeValue(QuestionAnswerController.class))
                .isEqualTo("hasAnyRole('ADMIN', 'SME')");
    }

    @Test
    void aiQuestionDraftControllerRemainsAdminAndSmeOnly() {
        assertThat(preAuthorizeValue(CourseAiQuestionDraftController.class))
                .isEqualTo("hasAnyRole('ADMIN', 'SME')");
    }

    private String preAuthorizeValue(Class<?> controllerClass) {
        PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(
                controllerClass,
                PreAuthorize.class
        );
        assertThat(annotation).isNotNull();
        return annotation.value();
    }

    private String preAuthorizeValue(Method method) {
        PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(
                method,
                PreAuthorize.class
        );
        assertThat(annotation).isNotNull();
        return annotation.value();
    }

    private Method method(Class<?> type, String name) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
