package com.smartlearnly.backend.assignment.definition.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartlearnly.backend.assignment.ai.controller.AssignmentAiDraftController;
import com.smartlearnly.backend.assignment.definition.dto.AssignmentModel;
import com.smartlearnly.backend.assignment.submission.controller.AssignmentSubmissionController;
import com.smartlearnly.backend.assignment.submission.dto.AssignmentSubmissionModel;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;

class AssignmentAuthorizationAnnotationTest {

    @Test
    void assignmentAuthoringShouldRejectTmo() throws Exception {
        Method create = AssignmentController.class.getMethod(
                "create", AssignmentModel.CreateRequest.class);
        Method update = AssignmentController.class.getMethod(
                "update", UUID.class, AssignmentModel.UpdateRequest.class);
        Method delete = AssignmentController.class.getMethod("delete", UUID.class);
        Method aiDraft = AssignmentAiDraftController.class.getMethod(
                "generateAiDraft",
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                MultipartFile.class);

        assertThat(preAuthorizeValue(create)).contains("ADMIN", "SME", "TRAINER").doesNotContain("TMO");
        assertThat(preAuthorizeValue(update)).contains("ADMIN", "SME", "TRAINER").doesNotContain("TMO");
        assertThat(preAuthorizeValue(delete)).contains("ADMIN", "SME", "TRAINER").doesNotContain("TMO");
        assertThat(preAuthorizeValue(aiDraft)).contains("ADMIN", "SME", "TRAINER").doesNotContain("TMO");
    }

    @Test
    void assignmentFilesCanBeUploadedByAuthorsAndTraineesButNotTmo() throws Exception {
        Method upload = AssignmentSubmissionController.class.getMethod(
                "uploadSubmissionFile", MultipartFile.class);

        assertThat(preAuthorizeValue(upload))
                .contains("ADMIN", "SME", "TRAINER", "TRAINEE")
                .doesNotContain("TMO");
    }

    @Test
    void traineeAndTmoCannotGradeAssignments() throws Exception {
        Method submit = AssignmentSubmissionController.class.getMethod(
                "submitAssignment", AssignmentSubmissionModel.CreateRequest.class);
        Method grade = AssignmentSubmissionController.class.getMethod(
                "gradeSubmission", UUID.class, AssignmentSubmissionModel.GradeRequest.class);

        assertThat(preAuthorizeValue(submit)).isEqualTo("hasRole('TRAINEE')");
        assertThat(preAuthorizeValue(grade))
                .contains("ADMIN", "SME", "TRAINER")
                .doesNotContain("TMO", "TRAINEE");
    }

    private String preAuthorizeValue(Method method) {
        PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(
                method,
                PreAuthorize.class);
        assertThat(annotation).isNotNull();
        return annotation.value();
    }
}
