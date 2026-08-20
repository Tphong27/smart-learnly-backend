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
    void courseQuestionReadBlockAllowsCourseAuthoringRolesWithoutAdmin() {
        assertThat(preAuthorizeValue(CourseQuestionController.class))
                .isEqualTo("hasAnyRole('SME', 'TMO', 'TRAINER')");
        assertThat(preAuthorizeValue(CourseModuleQuestionController.class))
                .isEqualTo("hasAnyRole('SME', 'TMO', 'TRAINER')");
    }

    @Test
    void courseQuestionMutationsRemainSmeOnly() {
        assertThat(preAuthorizeValue(method(CourseQuestionController.class, "create")))
                .isEqualTo("hasRole('SME')");
        assertThat(preAuthorizeValue(method(CourseQuestionController.class, "update")))
                .isEqualTo("hasRole('SME')");
        assertThat(preAuthorizeValue(method(CourseQuestionController.class, "archive")))
                .isEqualTo("hasRole('SME')");
        assertThat(preAuthorizeValue(method(CourseQuestionController.class, "importBatch")))
                .isEqualTo("hasRole('SME')");
        assertThat(preAuthorizeValue(method(CourseModuleQuestionController.class, "create")))
                .isEqualTo("hasRole('SME')");
        assertThat(preAuthorizeValue(method(CourseModuleQuestionController.class, "update")))
                .isEqualTo("hasRole('SME')");
        assertThat(preAuthorizeValue(method(CourseModuleQuestionController.class, "archive")))
                .isEqualTo("hasRole('SME')");
        assertThat(preAuthorizeValue(method(CourseModuleQuestionController.class, "importBatch")))
                .isEqualTo("hasRole('SME')");
    }

    @Test
    void questionMediaControllersRemainSmeOnly() {
        assertThat(preAuthorizeValue(QuestionMediaController.class))
                .isEqualTo("hasRole('SME')");
        assertThat(preAuthorizeValue(QuestionAnswerMediaController.class))
                .isEqualTo("hasRole('SME')");
        assertThat(preAuthorizeValue(QuestionAnswerController.class))
                .isEqualTo("hasRole('SME')");
    }

    @Test
    void aiQuestionDraftControllerRemainsSmeOnly() {
        assertThat(preAuthorizeValue(CourseAiQuestionDraftController.class))
                .isEqualTo("hasRole('SME')");
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
