package com.smartlearnly.backend.curriculum.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartlearnly.backend.curriculum.admin.controller.AdminCourseLessonController;
import com.smartlearnly.backend.curriculum.admin.controller.AdminCourseSectionController;
import com.smartlearnly.backend.curriculum.dto.LessonRequest;
import com.smartlearnly.backend.curriculum.dto.SectionRequest;
import com.smartlearnly.backend.flashcard.dto.AdminFlashcardDtos.CreateFlashcardLessonRequest;
import com.smartlearnly.backend.test.definition.dto.TestQuestionModel;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;

class TmoCurriculumAuthorizationTest {

        @Test
        void masterCurriculumWritesShouldAllowTmo() throws Exception {
                Method createSection = AdminCourseSectionController.class.getMethod(
                                "createSection", UUID.class, SectionRequest.class);
                Method createLesson = AdminCourseLessonController.class.getMethod(
                                "createLesson", UUID.class, LessonRequest.class);

                assertWriteAuthorizationIncludesTmo(createSection);
                assertWriteAuthorizationIncludesTmo(createLesson);
        }

        @Test
        void classCurriculumWritesShouldRejectTmo() throws Exception {
                Method initializeDraft = TrainerClassCurriculumController.class.getMethod(
                                "initializeDraft", UUID.class);
                Method createSection = TrainerClassCurriculumController.class.getMethod(
                                "createSection", UUID.class, SectionRequest.class);
                Method attachQuestion = TrainerLessonQuestionController.class.getMethod(
                                "attachQuestion", UUID.class, UUID.class, TestQuestionModel.AddRequest.class);
                Method createFlashcardSet = TrainerLessonFlashcardController.class.getMethod(
                                "createFlashcardSet", UUID.class, UUID.class, CreateFlashcardLessonRequest.class);
                Method uploadFlashcardImage = TrainerLessonFlashcardController.class.getMethod(
                                "uploadImage", UUID.class, UUID.class, UUID.class, MultipartFile.class);

                assertWriteAuthorizationIncludesTmo(initializeDraft);
                assertWriteAuthorizationIncludesTmo(createSection);
                assertWriteAuthorizationIncludesTmo(attachQuestion);
                assertWriteAuthorizationIncludesTmo(createFlashcardSet);
                assertWriteAuthorizationIncludesTmo(uploadFlashcardImage);
        }

        @Test
        void classCurriculumReadEndpointsShouldStillAllowTmo() throws Exception {
                Method getCurriculum = TrainerClassCurriculumController.class.getMethod(
                                "getCurriculum", UUID.class);

                assertThat(preAuthorizeValue(TrainerClassCurriculumController.class))
                                .contains("TMO");
                assertThat(AnnotatedElementUtils.findMergedAnnotation(getCurriculum, PreAuthorize.class))
                                .isNull();
        }

        private void assertWriteAuthorizationIncludesTmo(Method method) {
                assertThat(preAuthorizeValue(method))
                                .contains("TMO")
                                .doesNotContain("ADMIN");
        }

        private String preAuthorizeValue(Method method) {
                PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(
                                method,
                                PreAuthorize.class);
                assertThat(annotation).isNotNull();
                return annotation.value();
        }

        private String preAuthorizeValue(Class<?> controllerClass) {
                PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(
                                controllerClass,
                                PreAuthorize.class);
                assertThat(annotation).isNotNull();
                return annotation.value();
        }
}
