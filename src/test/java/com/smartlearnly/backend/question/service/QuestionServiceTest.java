package com.smartlearnly.backend.question.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.access.service.CourseAccessService;
import com.smartlearnly.backend.learning.module.entity.CourseModule;
import com.smartlearnly.backend.learning.module.repository.CourseModuleRepository;
import com.smartlearnly.backend.question.dto.QuestionImportDtos;
import com.smartlearnly.backend.question.dto.QuestionModel;
import com.smartlearnly.backend.question.entity.Question;
import com.smartlearnly.backend.question.entity.QuestionAnswer;
import com.smartlearnly.backend.question.entity.QuestionAnswerMediaAttachment;
import com.smartlearnly.backend.question.entity.QuestionMediaAttachment;
import com.smartlearnly.backend.question.entity.QuestionMediaType;
import com.smartlearnly.backend.question.entity.QuestionStatus;
import com.smartlearnly.backend.question.entity.QuestionType;
import com.smartlearnly.backend.question.repository.QuestionAnswerMediaAttachmentRepository;
import com.smartlearnly.backend.question.repository.QuestionAnswerRepository;
import com.smartlearnly.backend.question.repository.QuestionMediaAttachmentRepository;
import com.smartlearnly.backend.question.repository.QuestionRepository;
import com.smartlearnly.backend.test.repository.StudentTestAnswerRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class QuestionServiceTest {

    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private QuestionAnswerRepository answerRepository;
    @Mock
    private QuestionAnswerMediaAttachmentRepository answerMediaRepository;
    @Mock
    private QuestionMediaAttachmentRepository mediaAttachmentRepository;
    @Mock
    private CourseModuleRepository courseModuleRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private QuestionMediaImportService questionMediaImportService;
    @Mock
    private CourseAccessService courseAccessService;
    @Mock
    private StudentTestAnswerRepository studentTestAnswerRepository;

    private QuestionService service;
    private UUID courseId;
    private UUID otherCourseId;
    private UUID moduleId;
    private UUID questionId;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        service = new QuestionService(
                questionRepository,
                answerRepository,
                answerMediaRepository,
                mediaAttachmentRepository,
                courseModuleRepository,
                currentUserService,
                questionMediaImportService,
                courseAccessService,
                studentTestAnswerRepository
        );

        courseId = UUID.randomUUID();
        otherCourseId = UUID.randomUUID();
        moduleId = UUID.randomUUID();
        questionId = UUID.randomUUID();
        actorId = UUID.randomUUID();

        UserAccount actor = new UserAccount();
        actor.setId(actorId);
        actor.setEmail("sme@smartlearnly.dev");
        actor.setRole("SME");

        lenient().when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        lenient().when(courseModuleRepository.existsByIdAndCourseIdAndSystemFalseAndStatus(
                moduleId,
                courseId,
                CourseModule.STATUS_ACTIVE
        )).thenReturn(true);
        lenient().when(answerRepository.findByQuestionIdOrderByOrderIndexAsc(any())).thenReturn(List.of());
        lenient().when(answerMediaRepository.findByAnswerIdIn(any())).thenReturn(List.of());
        lenient().when(questionMediaImportService.validateMediaReferences(any(), any())).thenReturn(List.of());
        lenient().when(mediaAttachmentRepository.findByQuestionIdAndMediaTypeOrderByDisplayOrderAsc(
                any(UUID.class),
                any(QuestionMediaType.class)
        )).thenReturn(List.of());
    }

    @Test
    void listByCourse_returnsPagedQuestions_whenFiltersAreValid() {
        Question question = question(questionId, courseId, QuestionStatus.DRAFT);
        when(questionRepository.searchForAdmin(
                eq(courseId),
                eq(moduleId),
                eq("java"),
                eq("multiple_choice"),
                eq("draft"),
                eq(false),
                eq((short) 2),
                eq(PageRequest.of(0, 20))
        )).thenReturn(new PageImpl<>(List.of(question), PageRequest.of(0, 20), 1));

        var response = service.listByCourse(
                courseId,
                moduleId,
                " java ",
                "multiple-choice",
                "draft",
                false,
                (short) 2,
                0,
                20
        );

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).questionId()).isEqualTo(questionId);
        assertThat(response.items().get(0).questionType()).isEqualTo("multiple_choice");
        verify(courseAccessService).requireReadableCourse(courseId);
    }

    @Test
    void listByCourse_passesNullFilters_whenOptionalFiltersAreBlank() {
        when(questionRepository.searchForAdmin(
                eq(courseId),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(true),
                eq(null),
                eq(PageRequest.of(1, 10))
        )).thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 10), 0));

        var response = service.listByCourse(
                courseId,
                null,
                "   ",
                " ",
                null,
                true,
                null,
                1,
                10
        );

        assertThat(response.items()).isEmpty();
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(10);
    }

    @Test
    void getInCourse_returnsQuestionWithAnswersAndFirstMediaUrls() {
        UUID answerId = UUID.randomUUID();
        Question question = question(questionId, courseId, QuestionStatus.APPROVED);
        QuestionAnswer answer = answer(answerId, "Programming language", true, 1);
        QuestionMediaAttachment image = mediaAttachment(QuestionMediaType.IMAGE, "https://cdn.example.com/image.png", 1);
        QuestionMediaAttachment audio = mediaAttachment(QuestionMediaType.AUDIO, "https://cdn.example.com/audio.mp3", 1);
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(answerRepository.findByQuestionIdOrderByOrderIndexAsc(questionId)).thenReturn(List.of(answer));
        when(mediaAttachmentRepository.findByQuestionIdAndMediaTypeOrderByDisplayOrderAsc(questionId, QuestionMediaType.IMAGE))
                .thenReturn(List.of(image));
        when(mediaAttachmentRepository.findByQuestionIdAndMediaTypeOrderByDisplayOrderAsc(questionId, QuestionMediaType.AUDIO))
                .thenReturn(List.of(audio));
        when(mediaAttachmentRepository.findByQuestionIdAndMediaTypeOrderByDisplayOrderAsc(questionId, QuestionMediaType.VIDEO))
                .thenReturn(List.of());

        QuestionModel.Response response = service.getInCourse(courseId, questionId);

        assertThat(response.questionId()).isEqualTo(questionId);
        assertThat(response.status()).isEqualTo("approved");
        assertThat(response.imageUrl()).isEqualTo("https://cdn.example.com/image.png");
        assertThat(response.audioUrl()).isEqualTo("https://cdn.example.com/audio.mp3");
        assertThat(response.answerCount()).isEqualTo(1);
        assertThat(response.answers().get(0).answerText()).isEqualTo("Programming language");
        assertThat(response.mediaAttachments()).hasSize(2);
    }

    @Test
    void getInCourse_mapsAnswerMediaVideoAndDefaultOrders() {
        UUID answerId = UUID.randomUUID();
        Question question = question(questionId, courseId, QuestionStatus.APPROVED);
        question.setBloomLevel(null);
        question.setImportSource("excel_import");
        QuestionAnswer answer = answer(answerId, "Programming language", true, null);
        QuestionAnswerMediaAttachment answerMedia = answerMediaAttachment(answerId, QuestionMediaType.AUDIO, null, null);
        QuestionMediaAttachment video = mediaAttachment(QuestionMediaType.VIDEO, "https://cdn.example.com/video.mp4", 1);
        video.setFileSize(null);
        video.setDisplayOrder(null);
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(answerRepository.findByQuestionIdOrderByOrderIndexAsc(questionId)).thenReturn(List.of(answer));
        when(answerMediaRepository.findByAnswerIdIn(List.of(answerId))).thenReturn(List.of(answerMedia));
        when(mediaAttachmentRepository.findByQuestionIdAndMediaTypeOrderByDisplayOrderAsc(questionId, QuestionMediaType.IMAGE))
                .thenReturn(List.of());
        when(mediaAttachmentRepository.findByQuestionIdAndMediaTypeOrderByDisplayOrderAsc(questionId, QuestionMediaType.AUDIO))
                .thenReturn(List.of());
        when(mediaAttachmentRepository.findByQuestionIdAndMediaTypeOrderByDisplayOrderAsc(questionId, QuestionMediaType.VIDEO))
                .thenReturn(List.of(video));

        QuestionModel.Response response = service.getInCourse(courseId, questionId);

        assertThat(response.bloomLevel()).isNull();
        assertThat(response.imageUrl()).isNull();
        assertThat(response.audioUrl()).isNull();
        assertThat(response.mediaAttachments()).singleElement()
                .satisfies(media -> {
                    assertThat(media.mediaType()).isEqualTo("video");
                    assertThat(media.size()).isZero();
                    assertThat(media.displayOrder()).isZero();
                });
        assertThat(response.answers()).singleElement()
                .satisfies(mappedAnswer -> {
                    assertThat(mappedAnswer.orderIndex()).isZero();
                    assertThat(mappedAnswer.media()).singleElement()
                            .satisfies(media -> {
                                assertThat(media.mediaType()).isEqualTo("audio");
                                assertThat(media.size()).isZero();
                                assertThat(media.displayOrder()).isZero();
                            });
                });
    }

    @Test
    void getInCourse_throwsNotFound_whenQuestionDoesNotExist() {
        when(questionRepository.findById(questionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getInCourse(courseId, questionId))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

        verify(answerRepository, never()).findByQuestionIdOrderByOrderIndexAsc(any());
    }

    @Test
    void getInCourse_throwsNotFound_whenQuestionBelongsToAnotherCourse() {
        when(questionRepository.findById(questionId))
                .thenReturn(Optional.of(question(questionId, otherCourseId, QuestionStatus.DRAFT)));

        assertThatThrownBy(() -> service.getInCourse(courseId, questionId))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

        verify(courseAccessService).requireReadableCourse(courseId);
        verify(answerRepository, never()).findByQuestionIdOrderByOrderIndexAsc(any());
    }

    @Test
    void createForCourse_savesQuestionAndAnswers_whenRequestIsValid() {
        when(questionRepository.existsActiveDuplicateInCourse(courseId, "What is Java?", null))
                .thenReturn(false);
        when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> {
            Question saved = invocation.getArgument(0);
            saved.setId(questionId);
            return saved;
        });

        QuestionModel.Response response = service.createForCourse(courseId, createRequest());

        assertThat(response.questionId()).isEqualTo(questionId);
        assertThat(response.courseId()).isEqualTo(courseId);
        assertThat(response.moduleId()).isNull();
        assertThat(response.questionText()).isEqualTo("What is Java?");
        assertThat(response.status()).isEqualTo("draft");
        assertThat(response.createdBy()).isEqualTo(actorId);

        ArgumentCaptor<Question> questionCaptor = ArgumentCaptor.forClass(Question.class);
        verify(questionRepository).save(questionCaptor.capture());
        assertThat(questionCaptor.getValue().getModuleId()).isNull();
        assertThat(questionCaptor.getValue().getQuestionType()).isEqualTo(QuestionType.SINGLE_CHOICE);
        assertThat(questionCaptor.getValue().getIsAiGenerated()).isFalse();

        verify(answerRepository, never()).deleteByQuestionId(questionId);
        verify(answerRepository, times(2)).save(any(QuestionAnswer.class));
        verify(courseAccessService).requireUpdatableCourse(courseId);
    }

    /**
     * Xác nhận API module-scoped có thể tạo câu hỏi dù request body không chứa moduleId.
     * Module lưu vào entity phải luôn là module nhận từ path.
     */
    @Test
    void createForCourse_usesPathModule_whenModuleRequestHasNoModuleField() {
        when(questionRepository.existsActiveDuplicateInCourse(courseId, "What is Java?", null))
                .thenReturn(false);
        when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> {
            Question saved = invocation.getArgument(0);
            saved.setId(questionId);
            return saved;
        });
        QuestionModel.ModuleCreateRequest request = new QuestionModel.ModuleCreateRequest(
                "What is Java?",
                "single_choice",
                "remember",
                (short) 2,
                "Basic Java question",
                "draft",
                answers()
        );

        QuestionModel.Response response = service.createForCourse(courseId, moduleId, request);

        assertThat(response.moduleId()).isEqualTo(moduleId);
        ArgumentCaptor<Question> questionCaptor = ArgumentCaptor.forClass(Question.class);
        verify(questionRepository).save(questionCaptor.capture());
        assertThat(questionCaptor.getValue().getModuleId()).isEqualTo(moduleId);
    }

    /**
     * Xác nhận URL cũ chứa curriculum section ID vẫn được đổi sang canonical module ID.
     * Đây là trường hợp từng gây lỗi "Question module must belong to the selected course".
     */
    @Test
    void createForCourse_resolvesCurriculumSectionIdToCanonicalModuleId() {
        UUID sectionId = UUID.randomUUID();
        when(courseModuleRepository.existsByIdAndCourseIdAndSystemFalseAndStatus(
                sectionId,
                courseId,
                CourseModule.STATUS_ACTIVE
        )).thenReturn(false);
        when(courseModuleRepository.findActiveModuleIdByCourseIdAndSectionId(
                courseId,
                sectionId,
                CourseModule.STATUS_ACTIVE
        )).thenReturn(Optional.of(moduleId));
        when(questionRepository.existsActiveDuplicateInCourse(courseId, "What is Java?", null))
                .thenReturn(false);
        when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> {
            Question saved = invocation.getArgument(0);
            saved.setId(questionId);
            return saved;
        });
        QuestionModel.ModuleCreateRequest request = new QuestionModel.ModuleCreateRequest(
                "What is Java?",
                "single_choice",
                "remember",
                (short) 2,
                "Basic Java question",
                "draft",
                answers()
        );

        QuestionModel.Response response = service.createForCourse(courseId, sectionId, request);

        assertThat(response.moduleId()).isEqualTo(moduleId);
        ArgumentCaptor<Question> questionCaptor = ArgumentCaptor.forClass(Question.class);
        verify(questionRepository).save(questionCaptor.capture());
        assertThat(questionCaptor.getValue().getModuleId()).isEqualTo(moduleId);
    }

    @Test
    void createForCourse_defaultsStatusAndOrder_whenOptionalValuesAreBlank() {
        when(questionRepository.existsActiveDuplicateInCourse(courseId, "Java is platform independent.", null))
                .thenReturn(false);
        when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> {
            Question saved = invocation.getArgument(0);
            saved.setId(questionId);
            return saved;
        });
        QuestionModel.CreateRequest request = new QuestionModel.CreateRequest(
                null,
                moduleId,
                " Java is platform independent. ",
                "true_false",
                " ",
                (short) 1,
                " ",
                null,
                List.of(
                        new QuestionModel.AnswerRequest(null, null, "True", true, null, null, null),
                        new QuestionModel.AnswerRequest(null, null, "False", false, null, null, null)
                )
        );

        QuestionModel.Response response = service.createForCourse(courseId, request);

        assertThat(response.status()).isEqualTo("draft");
        assertThat(response.bloomLevel()).isNull();
        ArgumentCaptor<QuestionAnswer> answerCaptor = ArgumentCaptor.forClass(QuestionAnswer.class);
        verify(answerRepository, times(2)).save(answerCaptor.capture());
        assertThat(answerCaptor.getAllValues()).extracting(QuestionAnswer::getOrderIndex)
                .containsExactly(1, 2);
    }

    @Test
    void createForCourse_throwsBusinessRuleViolation_whenQuestionTextAlreadyExists() {
        when(questionRepository.existsActiveDuplicateInCourse(courseId, "What is Java?", null))
                .thenReturn(true);

        assertThatThrownBy(() -> service.createForCourse(courseId, createRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION))
                .hasMessageContaining("already exists in this course");

        verify(questionRepository, never()).save(any());
        verify(answerRepository, never()).save(any());
    }

    @Test
    void createForCourse_savesCourseWideQuestion_whenModuleIsMissing() {
        QuestionModel.CreateRequest request = new QuestionModel.CreateRequest(
                null,
                null,
                "What is Java?",
                "single_choice",
                "remember",
                (short) 2,
                "Basic Java question",
                "draft",
                answers()
        );
        when(questionRepository.existsActiveDuplicateInCourse(courseId, "What is Java?", null))
                .thenReturn(false);
        when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> {
            Question saved = invocation.getArgument(0);
            saved.setId(questionId);
            return saved;
        });

        QuestionModel.Response response = service.createForCourse(courseId, request);

        assertThat(response.moduleId()).isNull();
        verify(questionRepository).save(any(Question.class));
    }

    @Test
    void createForCourse_ignoresLegacyModuleFromCourseWideRequest() {
        UUID invalidModuleId = UUID.randomUUID();
        when(questionRepository.existsActiveDuplicateInCourse(courseId, "What is Java?", null))
                .thenReturn(false);
        when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> {
            Question saved = invocation.getArgument(0);
            saved.setId(questionId);
            return saved;
        });
        QuestionModel.CreateRequest request = new QuestionModel.CreateRequest(
                null,
                invalidModuleId,
                "What is Java?",
                "single_choice",
                "remember",
                (short) 2,
                "Basic Java question",
                "draft",
                answers()
        );

        QuestionModel.Response response = service.createForCourse(courseId, request);

        assertThat(response.moduleId()).isNull();
        verify(questionRepository).save(any(Question.class));
    }

    @Test
    void createForCourse_throwsInvalidRequest_whenQuestionTextIsBlank() {
        QuestionModel.CreateRequest request = new QuestionModel.CreateRequest(
                null,
                moduleId,
                " ",
                "single_choice",
                "remember",
                (short) 2,
                null,
                "draft",
                answers()
        );

        assertThatThrownBy(() -> service.createForCourse(courseId, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST))
                .hasMessageContaining("Question text is required");

        verify(questionRepository, never()).existsActiveDuplicateInCourse(any(), any(), any());
        verify(questionRepository, never()).save(any());
    }

    @Test
    void createForCourse_throwsInvalidRequest_whenStatusIsInvalid() {
        when(questionRepository.existsActiveDuplicateInCourse(courseId, "What is Java?", null))
                .thenReturn(false);
        QuestionModel.CreateRequest request = new QuestionModel.CreateRequest(
                null,
                moduleId,
                "What is Java?",
                "single_choice",
                "remember",
                (short) 2,
                null,
                "published",
                answers()
        );

        assertThatThrownBy(() -> service.createForCourse(courseId, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST))
                .hasMessageContaining("Question status is invalid");

        verify(questionRepository, never()).save(any());
    }

    @Test
    void createForCourse_throwsInvalidRequest_whenBloomLevelIsInvalid() {
        when(questionRepository.existsActiveDuplicateInCourse(courseId, "What is Java?", null))
                .thenReturn(false);
        QuestionModel.CreateRequest request = new QuestionModel.CreateRequest(
                null,
                moduleId,
                "What is Java?",
                "single_choice",
                "unknown",
                (short) 2,
                null,
                "draft",
                answers()
        );

        assertThatThrownBy(() -> service.createForCourse(courseId, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST))
                .hasMessageContaining("Bloom level is invalid");

        verify(questionRepository, never()).save(any());
    }

    @Test
    void createForCourse_throwsInvalidRequest_whenAnswersAreMissing() {
        QuestionModel.CreateRequest request = new QuestionModel.CreateRequest(
                null,
                moduleId,
                "What is Java?",
                "single_choice",
                "remember",
                (short) 2,
                null,
                "draft",
                null
        );

        assertThatThrownBy(() -> service.createForCourse(courseId, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST))
                .hasMessageContaining("At least two answers are required");

        verify(questionRepository, never()).save(any());
    }

    @Test
    void createForCourse_throwsInvalidRequest_whenAnswerTextIsBlank() {
        QuestionModel.CreateRequest request = new QuestionModel.CreateRequest(
                null,
                moduleId,
                "What is Java?",
                "single_choice",
                "remember",
                (short) 2,
                null,
                "draft",
                List.of(
                        answerRequest("Programming language", true, 1),
                        answerRequest(" ", false, 2)
                )
        );

        assertThatThrownBy(() -> service.createForCourse(courseId, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST))
                .hasMessageContaining("Answer text is required");

        verify(questionRepository, never()).save(any());
    }

    @Test
    void createForCourse_throwsInvalidRequest_whenTrueFalseHasWrongAnswerCount() {
        QuestionModel.CreateRequest request = new QuestionModel.CreateRequest(
                null,
                moduleId,
                "Java is a programming language.",
                "true_false",
                "remember",
                (short) 1,
                null,
                "draft",
                List.of(
                        answerRequest("True", true, 1),
                        answerRequest("False", false, 2),
                        answerRequest("Maybe", false, 3)
                )
        );

        assertThatThrownBy(() -> service.createForCourse(courseId, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST))
                .hasMessageContaining("exactly two answers");

        verify(questionRepository, never()).save(any());
    }

    @Test
    void createForCourse_throwsInvalidRequest_whenTrueFalseAnswersAreInvalid() {
        QuestionModel.CreateRequest request = new QuestionModel.CreateRequest(
                null,
                moduleId,
                "Java is a programming language.",
                "true_false",
                "remember",
                (short) 1,
                null,
                "draft",
                List.of(
                        new QuestionModel.AnswerRequest(null, null, "Yes", true, null, 1, null),
                        new QuestionModel.AnswerRequest(null, null, "No", false, null, 2, null)
                )
        );

        assertThatThrownBy(() -> service.createForCourse(courseId, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST))
                .hasMessageContaining("True/false answers must be True and False");

        verify(questionRepository, never()).save(any());
    }

    @Test
    void createForCourse_throwsInvalidRequest_whenQuestionTypeIsUnsupported() {
        QuestionModel.CreateRequest request = new QuestionModel.CreateRequest(
                null,
                moduleId,
                "Write a short essay.",
                "essay",
                "remember",
                (short) 2,
                null,
                "draft",
                answers()
        );

        assertThatThrownBy(() -> service.createForCourse(courseId, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST))
                .hasMessageContaining("Question type must be single_choice, multiple_choice, or true_false");

        verify(questionRepository, never()).existsActiveDuplicateInCourse(any(), any(), any());
        verify(questionRepository, never()).save(any());
    }

    @Test
    void createForCourse_throwsInvalidRequest_whenChoiceQuestionHasTooManyAnswers() {
        QuestionModel.CreateRequest request = new QuestionModel.CreateRequest(
                null,
                moduleId,
                "Choose one.",
                "single_choice",
                "remember",
                (short) 2,
                null,
                "draft",
                List.of(
                        answerRequest("A", true, 1),
                        answerRequest("B", false, 2),
                        answerRequest("C", false, 3),
                        answerRequest("D", false, 4),
                        answerRequest("E", false, 5),
                        answerRequest("F", false, 6),
                        answerRequest("G", false, 7))
        );

        assertThatThrownBy(() -> service.createForCourse(courseId, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST))
                .hasMessageContaining("2 to 6 answers");

        verify(questionRepository, never()).save(any());
    }

    @Test
    void createForCourse_throwsInvalidRequest_whenSingleChoiceCorrectAnswerCountIsNotOne() {
        QuestionModel.CreateRequest request = new QuestionModel.CreateRequest(
                null,
                moduleId,
                "Choose one.",
                "single_choice",
                "remember",
                (short) 2,
                null,
                "draft",
                List.of(
                        answerRequest("A", false, 1),
                        answerRequest("B", false, 2))
        );

        assertThatThrownBy(() -> service.createForCourse(courseId, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST))
                .hasMessageContaining("Exactly one correct answer is required");

        verify(questionRepository, never()).save(any());
    }

    @Test
    void updateInCourse_updatesQuestionAndAnswers_whenRequestIsValid() {
        Question existing = question(questionId, courseId, QuestionStatus.DRAFT);
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(existing));
        when(questionRepository.existsActiveDuplicateInCourse(courseId, "Updated question?", questionId))
                .thenReturn(false);
        when(questionRepository.save(existing)).thenReturn(existing);

        QuestionModel.UpdateRequest request = new QuestionModel.UpdateRequest(
                null,
                moduleId,
                "Updated question?",
                "single_choice",
                "understand",
                (short) 3,
                "Updated explanation",
                "pending_review",
                answers()
        );

        QuestionModel.Response response = service.updateInCourse(courseId, questionId, request);

        assertThat(response.questionText()).isEqualTo("Updated question?");
        assertThat(response.status()).isEqualTo("pending_review");
        assertThat(existing.getStatus()).isEqualTo(QuestionStatus.PENDING_REVIEW);
        verify(answerRepository, never()).deleteByQuestionId(questionId);
        verify(answerRepository, times(2)).save(any(QuestionAnswer.class));
    }

    @Test
    void updateInCourse_preservesAnswerIdsReferencedByQuizAttempts() {
        UUID correctAnswerId = UUID.randomUUID();
        UUID wrongAnswerId = UUID.randomUUID();
        Question existing = question(questionId, courseId, QuestionStatus.APPROVED);
        QuestionAnswer correctAnswer = answer(correctAnswerId, "True", true, 1);
        QuestionAnswer wrongAnswer = answer(wrongAnswerId, "False", false, 2);
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(existing));
        when(questionRepository.existsActiveDuplicateInCourse(courseId, "Updated statement", questionId))
                .thenReturn(false);
        when(questionRepository.save(existing)).thenReturn(existing);
        when(answerRepository.findByQuestionIdOrderByOrderIndexAsc(questionId))
                .thenReturn(List.of(correctAnswer, wrongAnswer));

        QuestionModel.UpdateRequest request = new QuestionModel.UpdateRequest(
                null,
                moduleId,
                "Updated statement",
                "true_false",
                null,
                null,
                null,
                "approved",
                List.of(
                        new QuestionModel.AnswerRequest(
                                correctAnswerId, correctAnswerId, "True", false, null, 1, null),
                        new QuestionModel.AnswerRequest(
                                wrongAnswerId, wrongAnswerId, "False", true, null, 2, null)));

        service.updateInCourse(courseId, questionId, request);

        assertThat(correctAnswer.getId()).isEqualTo(correctAnswerId);
        assertThat(correctAnswer.getIsCorrect()).isFalse();
        assertThat(wrongAnswer.getId()).isEqualTo(wrongAnswerId);
        assertThat(wrongAnswer.getIsCorrect()).isTrue();
        verify(answerRepository).save(correctAnswer);
        verify(answerRepository).save(wrongAnswer);
        verify(answerRepository, never()).deleteByQuestionId(questionId);
        verify(answerRepository, never()).existsStudentSelectionById(any());
    }

    @Test
    void updateInCourse_rejectsRemovingAnswerReferencedByQuizAttempt() {
        UUID retainedAnswerId = UUID.randomUUID();
        UUID secondRetainedAnswerId = UUID.randomUUID();
        UUID removedAnswerId = UUID.randomUUID();
        Question existing = question(questionId, courseId, QuestionStatus.APPROVED);
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(existing));
        when(questionRepository.existsActiveDuplicateInCourse(courseId, "Updated question?", questionId))
                .thenReturn(false);
        when(questionRepository.save(existing)).thenReturn(existing);
        when(answerRepository.findByQuestionIdOrderByOrderIndexAsc(questionId)).thenReturn(List.of(
                answer(retainedAnswerId, "A", true, 1),
                answer(secondRetainedAnswerId, "B", false, 2),
                answer(removedAnswerId, "C", false, 3)));
        when(answerRepository.existsStudentSelectionById(removedAnswerId)).thenReturn(true);
        QuestionModel.UpdateRequest request = new QuestionModel.UpdateRequest(
                null,
                moduleId,
                "Updated question?",
                "single_choice",
                null,
                null,
                null,
                "approved",
                List.of(
                        new QuestionModel.AnswerRequest(
                                retainedAnswerId, null, "A", true, null, 1, null),
                        new QuestionModel.AnswerRequest(
                                secondRetainedAnswerId, null, "B", false, null, 2, null)));

        assertThatThrownBy(() -> service.updateInCourse(courseId, questionId, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION))
                .hasMessage("An answer used in quiz attempts cannot be removed");

        verify(answerRepository, never()).deleteAll(any());
        verify(answerRepository, never()).deleteByQuestionId(any());
    }

    @Test
    void updateInCourse_throwsBusinessRuleViolation_whenQuestionIsArchived() {
        Question archived = question(questionId, courseId, QuestionStatus.ARCHIVED);
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(archived));

        assertThatThrownBy(() -> service.updateInCourse(courseId, questionId, updateRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION))
                .hasMessageContaining("Cannot update an archived question");

        verify(questionRepository, never()).save(any());
        verify(answerRepository, never()).deleteByQuestionId(any());
    }

    @Test
    void updateInCourse_throwsBusinessRuleViolation_whenQuestionAlreadyHasTraineeAnswers() {
        Question existing = question(questionId, courseId, QuestionStatus.DRAFT);
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(existing));
        when(studentTestAnswerRepository.existsByQuestionId(questionId)).thenReturn(true);

        assertThatThrownBy(() -> service.updateInCourse(courseId, questionId, updateRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION))
                .hasMessageContaining("Cannot update or delete a question that already has trainee answers");

        verify(questionRepository, never()).save(any());
        verify(answerRepository, never()).deleteByQuestionId(any());
    }

    @Test
    void updateInCourse_throwsNotFound_whenQuestionBelongsToAnotherCourse() {
        Question otherCourseQuestion = question(questionId, otherCourseId, QuestionStatus.DRAFT);
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(otherCourseQuestion));

        assertThatThrownBy(() -> service.updateInCourse(courseId, questionId, updateRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

        verify(questionRepository, never()).save(any());
        verify(answerRepository, never()).deleteByQuestionId(any());
    }

    @Test
    void updateInCourse_throwsBusinessRuleViolation_whenUpdatedTextDuplicatesAnotherQuestion() {
        Question existing = question(questionId, courseId, QuestionStatus.DRAFT);
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(existing));
        when(questionRepository.existsActiveDuplicateInCourse(courseId, "Updated question?", questionId))
                .thenReturn(true);

        assertThatThrownBy(() -> service.updateInCourse(courseId, questionId, updateRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION))
                .hasMessageContaining("already exists in this course");

        verify(questionRepository, never()).save(any());
        verify(answerRepository, never()).deleteByQuestionId(any());
    }

    @Test
    void updateInCourse_keepsCurrentStatus_whenStatusIsBlank() {
        Question existing = question(questionId, courseId, QuestionStatus.APPROVED);
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(existing));
        when(questionRepository.existsActiveDuplicateInCourse(courseId, "Updated question?", questionId))
                .thenReturn(false);
        when(questionRepository.save(existing)).thenReturn(existing);
        QuestionModel.UpdateRequest request = new QuestionModel.UpdateRequest(
                null,
                moduleId,
                "Updated question?",
                "single_choice",
                "understand",
                (short) 3,
                "Updated explanation",
                " ",
                answers()
        );

        QuestionModel.Response response = service.updateInCourse(courseId, questionId, request);

        assertThat(response.status()).isEqualTo("approved");
        assertThat(existing.getStatus()).isEqualTo(QuestionStatus.APPROVED);
    }

    @Test
    void archiveInCourse_setsArchivedStatus_whenQuestionIsActive() {
        Question question = question(questionId, courseId, QuestionStatus.DRAFT);
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(questionRepository.save(question)).thenReturn(question);

        service.archiveInCourse(courseId, questionId);

        assertThat(question.getStatus()).isEqualTo(QuestionStatus.ARCHIVED);
        verify(courseAccessService).requireUpdatableCourse(courseId);
        verify(questionRepository).save(question);
    }

    @Test
    void archiveInCourse_throwsBusinessRuleViolation_whenQuestionIsAlreadyArchived() {
        Question question = question(questionId, courseId, QuestionStatus.ARCHIVED);
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));

        assertThatThrownBy(() -> service.archiveInCourse(courseId, questionId))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION))
                .hasMessageContaining("already archived");

        verify(questionRepository, never()).save(any());
    }

    @Test
    void archiveInCourse_throwsNotFound_whenQuestionBelongsToAnotherCourse() {
        Question question = question(questionId, otherCourseId, QuestionStatus.DRAFT);
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));

        assertThatThrownBy(() -> service.archiveInCourse(courseId, questionId))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

        verify(questionRepository, never()).save(any());
    }

    @Test
    void restoreInCourse_setsDraftStatus_whenQuestionIsArchived() {
        Question question = question(questionId, courseId, QuestionStatus.ARCHIVED);
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(questionRepository.existsActiveDuplicateInCourse(courseId, question.getQuestionText(), questionId))
                .thenReturn(false);

        service.restoreInCourse(courseId, questionId);

        assertThat(question.getStatus()).isEqualTo(QuestionStatus.DRAFT);
        verify(courseAccessService).requireUpdatableCourse(courseId);
        verify(questionRepository).save(question);
    }

    @Test
    void restoreInCourse_throwsBusinessRuleViolation_whenQuestionIsNotArchived() {
        Question question = question(questionId, courseId, QuestionStatus.DRAFT);
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));

        assertThatThrownBy(() -> service.restoreInCourse(courseId, questionId))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION))
                .hasMessageContaining("Only archived questions");

        verify(questionRepository, never()).save(any());
    }

    @Test
    void restoreInCourse_throwsBusinessRuleViolation_whenActiveDuplicateExists() {
        Question question = question(questionId, courseId, QuestionStatus.ARCHIVED);
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(questionRepository.existsActiveDuplicateInCourse(courseId, question.getQuestionText(), questionId))
                .thenReturn(true);

        assertThatThrownBy(() -> service.restoreInCourse(courseId, questionId))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION))
                .hasMessageContaining("same text");

        verify(questionRepository, never()).save(any());
    }

    @Test
    void importBatchForCourse_savesReviewedRowsAndAttachesImportedMedia() {
        when(questionRepository.existsActiveDuplicateInCourse(courseId, "Imported question?", null))
                .thenReturn(false);
        when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> {
            Question saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        QuestionImportDtos.ImportRow row = importRow(
                2,
                "Imported question?",
                "single_choice",
                List.of("A", "B"),
                "B",
                moduleId);

        QuestionImportDtos.ImportBatchResponse response = service.importBatchForCourse(
                courseId,
                new QuestionImportDtos.ImportBatchRequest(List.of(row), "excel"));

        assertThat(response.requested()).isEqualTo(1);
        assertThat(response.created()).isEqualTo(1);
        assertThat(response.createdQuestionIds()).hasSize(1);
        ArgumentCaptor<Question> importedQuestion = ArgumentCaptor.forClass(Question.class);
        verify(questionRepository).save(importedQuestion.capture());
        assertThat(importedQuestion.getValue().getModuleId()).isNull();
        verify(questionMediaImportService).attachImportedMedia(
                any(Question.class),
                eq(row.imageFiles()),
                eq(row.audioFiles()),
                eq("excel_import"));
        verify(answerRepository, never())
                .deleteByQuestionId(response.createdQuestionIds().get(0));
        verify(answerRepository, times(2)).save(any(QuestionAnswer.class));
    }

    /**
     * Xác nhận URL của module A không thể sửa câu hỏi thuộc module B.
     * Service trả RESOURCE_NOT_FOUND để không làm lộ bản ghi ở module khác.
     */
    @Test
    void updateInCourse_throwsNotFound_whenQuestionBelongsToAnotherModule() {
        UUID otherModuleId = UUID.randomUUID();
        Question existing = question(questionId, courseId, QuestionStatus.DRAFT);
        existing.setModuleId(otherModuleId);
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(existing));
        QuestionModel.ModuleUpdateRequest request = new QuestionModel.ModuleUpdateRequest(
                "Updated question?",
                "single_choice",
                "understand",
                (short) 3,
                "Updated explanation",
                "pending_review",
                answers()
        );

        assertThatThrownBy(() -> service.updateInCourse(courseId, moduleId, questionId, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

        verify(questionRepository, never()).save(any());
        verify(answerRepository, never()).deleteByQuestionId(any());
    }

    @Test
    void importBatchForCourse_reportsRowNumber_whenMediaDownloadFails() {
        when(questionRepository.existsActiveDuplicateInCourse(courseId, "Imported question?", null))
                .thenReturn(false);
        when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> {
            Question saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        QuestionImportDtos.ImportRow row = importRow(
                7,
                "Imported question?",
                "single_choice",
                List.of("A", "B"),
                "B",
                moduleId);
        doThrow(new BusinessException(
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                "Media URL returned HTTP 404"
        )).when(questionMediaImportService).attachImportedMedia(
                any(Question.class),
                eq(row.imageFiles()),
                eq(row.audioFiles()),
                eq("excel_import"));

        assertThatThrownBy(() -> service.importBatchForCourse(
                courseId,
                new QuestionImportDtos.ImportBatchRequest(List.of(row), "excel")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE))
                .hasMessageContaining("Row 7 media import failed")
                .hasMessageContaining("HTTP 404");
    }

    @Test
    void importBatchForCourse_defaultsUnknownImportSourceToExcel() {
        when(questionRepository.existsActiveDuplicateInCourse(courseId, "Imported question?", null))
                .thenReturn(false);
        when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> {
            Question saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        QuestionImportDtos.ImportRow row = importRow(
                2,
                "Imported question?",
                "single_choice",
                List.of("A", "B"),
                "A",
                moduleId);

        service.importBatchForCourse(
                courseId,
                new QuestionImportDtos.ImportBatchRequest(List.of(row), "manual-upload"));

        verify(questionMediaImportService).attachImportedMedia(
                any(Question.class),
                eq(row.imageFiles()),
                eq(row.audioFiles()),
                eq("excel_import"));
    }

    @Test
    void importBatchForCourse_defaultsRemovedImportSourceToExcel() {
        when(questionRepository.existsActiveDuplicateInCourse(courseId, "Imported question?", null))
                .thenReturn(false);
        when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> {
            Question saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        QuestionImportDtos.ImportRow row = importRow(
                2,
                "Imported question?",
                "single_choice",
                List.of("A", "B"),
                "A",
                moduleId);

        service.importBatchForCourse(
                courseId,
                new QuestionImportDtos.ImportBatchRequest(List.of(row), "removed-import"));

        verify(questionMediaImportService).attachImportedMedia(
                any(Question.class),
                eq(row.imageFiles()),
                eq(row.audioFiles()),
                eq("excel_import"));
    }

    @Test
    void importReviewedRowsForCourse_marksQuestionsAsAiGeneratedAndUsesImportSourceForMedia() {
        when(questionRepository.existsActiveDuplicateInCourse(courseId, "AI imported?", null))
                .thenReturn(false);
        when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> {
            Question saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        QuestionImportDtos.ImportRow row = importRow(
                6,
                "AI imported?",
                "single_choice",
                List.of("A", "B", "C"),
                "C",
                moduleId);

        List<Question> saved = service.importReviewedRowsForCourse(courseId, List.of(row), true, "ai_generation");

        assertThat(saved).singleElement()
                .satisfies(question -> {
                    assertThat(question.getIsAiGenerated()).isTrue();
                    assertThat(question.getImportSource()).isEqualTo("ai_generation");
                });
        verify(questionMediaImportService).attachImportedMedia(
                any(Question.class),
                eq(row.imageFiles()),
                eq(row.audioFiles()),
                eq("ai_generation"));
    }

    @Test
    void importReviewedRowsForCourse_handlesTrueFalseFalseAnswer() {
        when(questionRepository.existsActiveDuplicateInCourse(courseId, "Java is only a database.", null))
                .thenReturn(false);
        when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> {
            Question saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        QuestionImportDtos.ImportRow row = importRow(
                7,
                "Java is only a database.",
                "true_false",
                List.of("True", "False"),
                "false",
                moduleId);

        service.importReviewedRowsForCourse(courseId, List.of(row), false, null);

        ArgumentCaptor<QuestionAnswer> answerCaptor = ArgumentCaptor.forClass(QuestionAnswer.class);
        verify(answerRepository, times(2)).save(answerCaptor.capture());
        assertThat(answerCaptor.getAllValues()).extracting(QuestionAnswer::getAnswerText)
                .containsExactly("True", "False");
        assertThat(answerCaptor.getAllValues()).extracting(QuestionAnswer::getIsCorrect)
                .containsExactly(false, true);
    }

    @Test
    void importReviewedRowsForCourse_handlesTrueFalseTrueAnswerWhenTrueIsSecondOption() {
        when(questionRepository.existsActiveDuplicateInCourse(courseId, "Java is a programming language.", null))
                .thenReturn(false);
        when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> {
            Question saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        QuestionImportDtos.ImportRow row = importRow(
                8,
                "Java is a programming language.",
                "true_false",
                List.of("False", "True"),
                "true",
                moduleId);

        service.importReviewedRowsForCourse(courseId, List.of(row), false, null);

        ArgumentCaptor<QuestionAnswer> answerCaptor = ArgumentCaptor.forClass(QuestionAnswer.class);
        verify(answerRepository, times(2)).save(answerCaptor.capture());
        assertThat(answerCaptor.getAllValues()).extracting(QuestionAnswer::getIsCorrect)
                .containsExactly(false, true);
    }

    @Test
    void importReviewedRowsForCourse_throwsInvalidRequest_whenRowsAreEmpty() {
        assertThatThrownBy(() -> service.importReviewedRowsForCourse(courseId, List.of(), false, null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST))
                .hasMessageContaining("At least one question row is required");

        verify(questionRepository, never()).save(any());
    }

    @Test
    void importBatchForCourse_throwsValidationFailed_whenRowContainsInvalidCorrectAnswer() {
        QuestionImportDtos.ImportRow row = importRow(
                4,
                "Imported question?",
                "single_choice",
                List.of("A", "B"),
                "C",
                moduleId);

        assertThatThrownBy(() -> service.importBatchForCourse(
                courseId,
                new QuestionImportDtos.ImportBatchRequest(List.of(row), "excel")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED))
                .hasMessageContaining("Correct answer refers to an option that was not provided");

        verify(questionRepository, never()).save(any());
        verify(answerRepository, never()).save(any());
    }

    @Test
    void importBatchForCourse_throwsValidationFailed_whenDuplicateExistsAfterRowValidation() {
        QuestionImportDtos.ImportRow row = importRow(
                4,
                "Imported question?",
                "single_choice",
                List.of("A", "B"),
                "A",
                moduleId);
        when(questionRepository.existsActiveDuplicateInCourse(courseId, "Imported question?", null))
                .thenReturn(true);

        assertThatThrownBy(() -> service.importBatchForCourse(
                courseId,
                new QuestionImportDtos.ImportBatchRequest(List.of(row), "excel")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED))
                .hasMessageContaining("already exists in this course");

        verify(questionRepository, never()).save(any());
        verify(answerRepository, never()).save(any());
    }

    @Test
    void importBatchForCourse_reportsOnlyFirstFiveRowErrorsAndRemainingCount() {
        List<QuestionImportDtos.ImportRow> rows = List.of(
                importRow(1, " ", "multiple_choice", List.of("A", "B"), "A", moduleId),
                importRow(2, "Question 2", "essay", List.of("A", "B"), "A", moduleId),
                importRow(3, "Question 3", "multiple_choice", List.of("A"), "A", moduleId),
                importRow(4, "Question 4", "multiple_choice", List.of("A", "B", "C", "D", "E", "F", "G"), "A", moduleId),
                importRow(5, "Question 5", "multiple_choice", List.of("A", "B"), "AA", moduleId),
                importRow(6, "Question 6", "multiple_choice", List.of("A", "B"), "Z", moduleId)
        );

        assertThatThrownBy(() -> service.importBatchForCourse(
                courseId,
                new QuestionImportDtos.ImportBatchRequest(rows, "excel")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED))
                .hasMessageContaining("Row 1")
                .hasMessageContaining("Row 5")
                .hasMessageContaining("And 1 more rows with errors")
                .satisfies(throwable -> assertThat(throwable).hasMessageNotContaining("Row 6"));

        verify(questionRepository, never()).save(any());
    }

    @Test
    void importBatchForCourse_collectsBoundaryValidationErrors() {
        UUID invalidModuleId = UUID.randomUUID();
        when(questionMediaImportService.validateMediaReferences(List.of("bad-image"), List.of("bad-audio")))
                .thenReturn(List.of("Image URL is invalid", "Audio URL is invalid"));
        QuestionImportDtos.ImportRow row = importRow(
                9,
                "Q".repeat(10001),
                null,
                Arrays.asList(null, "A".repeat(4001)),
                null,
                invalidModuleId,
                "E".repeat(10001),
                (short) 6,
                "not-a-bloom-level",
                List.of("bad-image"),
                List.of("bad-audio"));

        assertThatThrownBy(() -> service.importBatchForCourse(
                courseId,
                new QuestionImportDtos.ImportBatchRequest(List.of(row), "excel")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED))
                .hasMessageContaining("Question text must not exceed 10000 characters")
                .hasMessageContaining("Question type is required")
                .hasMessageContaining("Answer A is required")
                .hasMessageContaining("Answer B must not exceed 4000 characters")
                .hasMessageContaining("Correct answer is required")
                .hasMessageContaining("Difficulty must be between 1 and 5")
                .hasMessageContaining("Bloom level is invalid")
                .hasMessageContaining("Explanation must not exceed 10000 characters")
                .hasMessageContaining("Image URL is invalid");

        verify(questionRepository, never()).save(any());
    }

    @Test
    void importBatchForCourse_collectsTrueFalseValidationErrors() {
        QuestionImportDtos.ImportRow row = importRow(
                10,
                "Java has exactly one keyword.",
                "true_false",
                List.of("Yes", "No", "Maybe"),
                "A",
                moduleId);

        assertThatThrownBy(() -> service.importBatchForCourse(
                courseId,
                new QuestionImportDtos.ImportBatchRequest(List.of(row), "excel")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED))
                .hasMessageContaining("True/false questions must have exactly two answers")
                .hasMessageContaining("True/false answers must be True and False")
                .hasMessageContaining("Correct answer for true/false must be True or False");

        verify(questionRepository, never()).save(any());
    }

    private QuestionModel.CreateRequest createRequest() {
        return new QuestionModel.CreateRequest(
                null,
                moduleId,
                "What is Java?",
                "single_choice",
                "remember",
                (short) 2,
                "Basic Java question",
                "draft",
                answers()
        );
    }

    private QuestionModel.UpdateRequest updateRequest() {
        return new QuestionModel.UpdateRequest(
                null,
                moduleId,
                "Updated question?",
                "single_choice",
                "understand",
                (short) 3,
                "Updated explanation",
                "pending_review",
                answers()
        );
    }

    private List<QuestionModel.AnswerRequest> answers() {
        return List.of(
                answerRequest("Programming language", true, 1),
                answerRequest("Database", false, 2)
        );
    }

    private QuestionModel.AnswerRequest answerRequest(String text, boolean correct, int order) {
        return new QuestionModel.AnswerRequest(null, null, text, correct, null, order, null);
    }

    private QuestionAnswer answer(UUID id, String text, boolean correct, Integer orderIndex) {
        QuestionAnswer answer = new QuestionAnswer();
        answer.setId(id);
        answer.setQuestionId(questionId);
        answer.setAnswerText(text);
        answer.setIsCorrect(correct);
        answer.setOrderIndex(orderIndex);
        return answer;
    }

    private QuestionMediaAttachment mediaAttachment(QuestionMediaType type, String url, int displayOrder) {
        QuestionMediaAttachment attachment = new QuestionMediaAttachment();
        attachment.setId(UUID.randomUUID());
        attachment.setQuestionId(questionId);
        attachment.setMediaType(type);
        attachment.setMediaUrl(url);
        attachment.setObjectKey("questions/media-" + displayOrder);
        attachment.setBucket("question-media");
        attachment.setContentType(type == QuestionMediaType.IMAGE ? "image/png" : "audio/mpeg");
        attachment.setFileSize(10L);
        attachment.setOriginalFileName("media-" + displayOrder);
        attachment.setDisplayOrder(displayOrder);
        attachment.setImportSource("manual");
        return attachment;
    }

    private QuestionImportDtos.ImportRow importRow(
            int rowNumber,
            String text,
            String type,
            List<String> options,
            String correctAnswer,
            UUID rowModuleId
    ) {
        return importRow(
                rowNumber,
                text,
                type,
                options,
                correctAnswer,
                rowModuleId,
                "Explanation",
                (short) 2,
                "remember",
                List.of("https://example.com/question.png"),
                List.of("https://example.com/question.mp3"));
    }

    private QuestionImportDtos.ImportRow importRow(
            int rowNumber,
            String text,
            String type,
            List<String> options,
            String correctAnswer,
            UUID rowModuleId,
            String explanation,
            Short difficulty,
            String bloomLevel,
            List<String> imageFiles,
            List<String> audioFiles
    ) {
        return new QuestionImportDtos.ImportRow(
                rowNumber,
                text,
                type,
                options,
                correctAnswer,
                explanation,
                difficulty,
                bloomLevel,
                rowModuleId,
                imageFiles,
                audioFiles);
    }

    private Question question(UUID id, UUID owningCourseId, QuestionStatus status) {
        Question question = new Question();
        question.setId(id);
        question.setCourseId(owningCourseId);
        question.setModuleId(moduleId);
        question.setQuestionText("What is Java?");
        question.setQuestionType(QuestionType.MULTIPLE_CHOICE);
        question.setDifficulty((short) 2);
        question.setExplanation("Basic Java question");
        question.setIsAiGenerated(false);
        question.setStatus(status);
        question.setCreatedBy(actorId);
        return question;
    }

    private QuestionAnswerMediaAttachment answerMediaAttachment(
            UUID answerId,
            QuestionMediaType type,
            Long fileSize,
            Integer displayOrder
    ) {
        QuestionAnswerMediaAttachment attachment = new QuestionAnswerMediaAttachment();
        attachment.setId(UUID.randomUUID());
        attachment.setAnswerId(answerId);
        attachment.setMediaType(type);
        attachment.setMediaUrl("https://cdn.example.com/answer-media");
        attachment.setObjectKey("answers/media");
        attachment.setBucket("question-answer-media");
        attachment.setContentType(type == QuestionMediaType.AUDIO ? "audio/mpeg" : "image/png");
        attachment.setFileSize(fileSize);
        attachment.setOriginalFileName("answer-media");
        attachment.setDisplayOrder(displayOrder);
        attachment.setImportSource("manual");
        return attachment;
    }
}
