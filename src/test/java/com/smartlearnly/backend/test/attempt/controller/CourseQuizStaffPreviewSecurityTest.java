package com.smartlearnly.backend.test.attempt.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartlearnly.backend.test.attempt.dto.StudentTestAnswerModel;
import com.smartlearnly.backend.test.attempt.dto.TestAttemptModel;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class CourseQuizStaffPreviewSecurityTest {

    @Test
    void tmoShouldBeAllowedThroughEveryQuizAttemptWriteEndpoint() throws Exception {
        Method start = TestAttemptController.class.getMethod(
                "startAttempt", TestAttemptModel.StartRequest.class);
        Method submit = TestAttemptController.class.getMethod(
                "submitAttempt", UUID.class, TestAttemptModel.SubmitRequest.class);
        Method reopen = TestAttemptController.class.getMethod(
                "reopenAttempt", UUID.class);
        Method saveAnswer = StudentTestAnswerController.class.getMethod(
                "saveStudentAnswer", StudentTestAnswerModel.SaveRequest.class);

        assertThat(start.getAnnotation(PreAuthorize.class).value()).contains("'TMO'");
        assertThat(submit.getAnnotation(PreAuthorize.class).value()).contains("'TMO'");
        assertThat(reopen.getAnnotation(PreAuthorize.class).value()).contains("'TMO'");
        assertThat(saveAnswer.getAnnotation(PreAuthorize.class).value()).contains("'TMO'");
    }
}
