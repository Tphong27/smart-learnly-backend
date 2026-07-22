package com.smartlearnly.backend.assignment.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartlearnly.backend.assignment.dto.AssignmentModel;
import com.smartlearnly.backend.assignment.dto.AssignmentSubmissionModel;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;

class AssignmentAuthorizationAnnotationTest {

    @Test
    void assignmentAuthoringShouldRemainStaffOnly() throws Exception {
        Method create = AssignmentController.class.getMethod(
                "create", AssignmentModel.CreateRequest.class);
        Method update = AssignmentController.class.getMethod(
                "update", UUID.class, AssignmentModel.UpdateRequest.class);
        Method delete = AssignmentController.class.getMethod("delete", UUID.class);
        Method aiDraft = AssignmentController.class.getMethod(
                "generateAiDraft",
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                MultipartFile.class);

        assertThat(preAuthorizeValue(create)).contains("ADMIN", "TMO", "SME", "TRAINER");
        assertThat(preAuthorizeValue(update)).contains("ADMIN", "TMO", "SME", "TRAINER");
        assertThat(preAuthorizeValue(delete)).contains("ADMIN", "TMO", "SME", "TRAINER");
        assertThat(preAuthorizeValue(aiDraft)).contains("ADMIN", "TMO", "SME", "TRAINER");
    }

    @Test
    void assignmentFilesCanBeUploadedByStaffAndTrainees() throws Exception {
        Method upload = AssignmentSubmissionController.class.getMethod(
                "uploadSubmissionFile", MultipartFile.class);

        assertThat(preAuthorizeValue(upload))
                .contains("ADMIN", "TMO", "SME", "TRAINER", "TRAINEE");
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
