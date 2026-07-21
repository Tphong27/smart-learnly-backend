package com.smartlearnly.backend.assignment.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartlearnly.backend.assignment.dto.AssignmentModel;
import com.smartlearnly.backend.assignment.dto.AssignmentSubmissionModel;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;

class AssignmentAuthorizationAnnotationTest {

    @Test
    void assignmentAuthoringShouldRemainStaffOnly() throws Exception {
        Method create = AssignmentController.class.getMethod(
                "create", AssignmentModel.CreateRequest.class);
        Method update = AssignmentController.class.getMethod(
                "update", UUID.class, AssignmentModel.UpdateRequest.class);
        Method delete = AssignmentController.class.getMethod("delete", UUID.class);

        assertThat(preAuthorizeValue(create)).contains("ADMIN", "TMO", "SME", "TRAINER");
        assertThat(preAuthorizeValue(update)).contains("ADMIN", "TMO", "SME", "TRAINER");
        assertThat(preAuthorizeValue(delete)).contains("ADMIN", "TMO", "SME", "TRAINER");
    }

    @Test
    void traineeCanSubmitButCannotGradeAssignments() throws Exception {
        Method submit = AssignmentSubmissionController.class.getMethod(
                "submitAssignment", AssignmentSubmissionModel.CreateRequest.class);
        Method grade = AssignmentSubmissionController.class.getMethod(
                "gradeSubmission", UUID.class, AssignmentSubmissionModel.GradeRequest.class);

        assertThat(preAuthorizeValue(submit)).isEqualTo("hasRole('TRAINEE')");
        assertThat(preAuthorizeValue(grade)).contains("ADMIN", "TMO", "SME", "TRAINER");
    }

    private String preAuthorizeValue(Method method) {
        PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(
                method,
                PreAuthorize.class);
        assertThat(annotation).isNotNull();
        return annotation.value();
    }
}
