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
    void courseQuestionMutationsAllowSmeAndTrainer() {
        assertThat(preAuthorizeValue(method(CourseQuestionController.class, "create")))
                .isEqualTo("hasAnyRole('SME', 'TRAINER')");
        assertThat(preAuthorizeValue(method(CourseQuestionController.class, "update")))
                .isEqualTo("hasAnyRole('SME', 'TRAINER')");
        assertThat(preAuthorizeValue(method(CourseQuestionController.class, "archive")))
                .isEqualTo("hasAnyRole('SME', 'TRAINER')");
        assertThat(preAuthorizeValue(method(CourseQuestionController.class, "importBatch")))
                .isEqualTo("hasAnyRole('SME', 'TRAINER')");
        assertThat(preAuthorizeValue(method(CourseModuleQuestionController.class, "create")))
                .isEqualTo("hasAnyRole('SME', 'TRAINER')");
        assertThat(preAuthorizeValue(method(CourseModuleQuestionController.class, "update")))
                .isEqualTo("hasAnyRole('SME', 'TRAINER')");
        assertThat(preAuthorizeValue(method(CourseModuleQuestionController.class, "archive")))
                .isEqualTo("hasAnyRole('SME', 'TRAINER')");
        assertThat(preAuthorizeValue(method(CourseModuleQuestionController.class, "importBatch")))
                .isEqualTo("hasAnyRole('SME', 'TRAINER')");
    }

    @Test
    void questionMediaControllersAllowSmeAndTrainer() {
        assertThat(preAuthorizeValue(QuestionMediaController.class))
                .isEqualTo("hasAnyRole('SME', 'TRAINER')");
        assertThat(preAuthorizeValue(QuestionAnswerMediaController.class))
                .isEqualTo("hasAnyRole('SME', 'TRAINER')");
        assertThat(preAuthorizeValue(QuestionAnswerController.class))
                .isEqualTo("hasAnyRole('SME', 'TRAINER')");
    }

    @Test
    void aiQuestionDraftControllerAllowsSmeAndTrainer() {
        assertThat(preAuthorizeValue(CourseAiQuestionDraftController.class))
                .isEqualTo("hasAnyRole('SME', 'TRAINER')");
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
