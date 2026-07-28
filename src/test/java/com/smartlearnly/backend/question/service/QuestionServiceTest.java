package com.smartlearnly.backend.question.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.service.CourseAccessService;
import com.smartlearnly.backend.learning.module.entity.CourseModule;
import com.smartlearnly.backend.learning.module.repository.CourseModuleRepository;
import com.smartlearnly.backend.question.dto.QuestionImportDtos;
import com.smartlearnly.backend.question.dto.QuestionModel;
import com.smartlearnly.backend.question.entity.Question;
import com.smartlearnly.backend.question.entity.QuestionAnswer;
import com.smartlearnly.backend.question.entity.QuestionMediaAttachment;
import com.smartlearnly.backend.question.entity.QuestionMediaType;
import com.smartlearnly.backend.question.entity.QuestionStatus;
import com.smartlearnly.backend.question.entity.QuestionType;
import com.smartlearnly.backend.question.repository.QuestionAnswerMediaAttachmentRepository;
import com.smartlearnly.backend.question.repository.QuestionAnswerRepository;
import com.smartlearnly.backend.question.repository.QuestionMediaAttachmentRepository;
import com.smartlearnly.backend.question.repository.QuestionRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
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
                courseAccessService
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
                eq((short) 2),
                eq(PageRequest.of(0, 20))
        )).thenReturn(new PageImpl<>(List.of(question), PageRequest.of(0, 20), 1));

        var response = service.listByCourse(
                courseId,
                moduleId,
                " java ",
                "multiple-choice",
                "draft",
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
        assertThat(response.moduleId()).isEqualTo(moduleId);
        assertThat(response.questionText()).isEqualTo("What is Java?");
        assertThat(response.status()).isEqualTo("draft");
        assertThat(response.createdBy()).isEqualTo(actorId);

        ArgumentCaptor<Question> questionCaptor = ArgumentCaptor.forClass(Question.class);
        verify(questionRepository).save(questionCaptor.capture());
        assertThat(questionCaptor.getValue().getQuestionType()).isEqualTo(QuestionType.MULTIPLE_CHOICE);
        assertThat(questionCaptor.getValue().getIsAiGenerated()).isFalse();

        verify(answerRepository).deleteByQuestionId(questionId);
        verify(answerRepository, times(2)).save(any(QuestionAnswer.class));
        verify(courseAccessService).requireUpdatableCourse(courseId);
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
    void createForCourse_throwsInvalidRequest_whenModuleIsMissing() {
        QuestionModel.CreateRequest request = new QuestionModel.CreateRequest(
                null,
                null,
                "What is Java?",
                "multiple_choice",
                "remember",
                (short) 2,
                "Basic Java question",
                "draft",
                answers()
        );
        when(questionRepository.existsActiveDuplicateInCourse(courseId, "What is Java?", null))
                .thenReturn(false);

        assertThatThrownBy(() -> service.createForCourse(courseId, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST))
                .hasMessageContaining("Question module is required");

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
                .hasMessageContaining("Question type must be multiple_choice or true_false");

        verify(questionRepository, never()).existsActiveDuplicateInCourse(any(), any(), any());
        verify(questionRepository, never()).save(any());
    }

    @Test
    void createForCourse_throwsInvalidRequest_whenMultipleChoiceHasTooManyAnswers() {
        QuestionModel.CreateRequest request = new QuestionModel.CreateRequest(
                null,
                moduleId,
                "Choose one.",
                "multiple_choice",
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
    void createForCourse_throwsInvalidRequest_whenCorrectAnswerCountIsNotOne() {
        QuestionModel.CreateRequest request = new QuestionModel.CreateRequest(
                null,
                moduleId,
                "Choose one.",
                "multiple_choice",
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
    void updateInCourse_replacesQuestionAndAnswers_whenRequestIsValid() {
        Question existing = question(questionId, courseId, QuestionStatus.DRAFT);
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(existing));
        when(questionRepository.existsActiveDuplicateInCourse(courseId, "Updated question?", questionId))
                .thenReturn(false);
        when(questionRepository.save(existing)).thenReturn(existing);

        QuestionModel.UpdateRequest request = new QuestionModel.UpdateRequest(
                null,
                moduleId,
                "Updated question?",
                "multiple_choice",
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
        verify(answerRepository).deleteByQuestionId(questionId);
        verify(answerRepository, times(2)).save(any(QuestionAnswer.class));
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
                "multiple_choice",
                List.of("A", "B"),
                "B",
                moduleId);

        QuestionImportDtos.ImportBatchResponse response = service.importBatchForCourse(
                courseId,
                new QuestionImportDtos.ImportBatchRequest(List.of(row), "excel"));

        assertThat(response.requested()).isEqualTo(1);
        assertThat(response.created()).isEqualTo(1);
        assertThat(response.createdQuestionIds()).hasSize(1);
        verify(questionMediaImportService).attachImportedMedia(
                any(Question.class),
                eq(row.imageFiles()),
                eq(row.audioFiles()),
                eq("excel_import"));
        verify(answerRepository).deleteByQuestionId(response.createdQuestionIds().get(0));
        verify(answerRepository, times(2)).save(any(QuestionAnswer.class));
    }

    @Test
    void importBatchForCourse_throwsValidationFailed_whenRowContainsInvalidCorrectAnswer() {
        QuestionImportDtos.ImportRow row = importRow(
                4,
                "Imported question?",
                "multiple_choice",
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

    private QuestionModel.CreateRequest createRequest() {
        return new QuestionModel.CreateRequest(
                null,
                moduleId,
                "What is Java?",
                "multiple_choice",
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
                "multiple_choice",
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

    private QuestionAnswer answer(UUID id, String text, boolean correct, int orderIndex) {
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
        return new QuestionImportDtos.ImportRow(
                rowNumber,
                text,
                type,
                options,
                correctAnswer,
                "Explanation",
                (short) 2,
                "remember",
                rowModuleId,
                List.of("https://example.com/question.png"),
                List.of("https://example.com/question.mp3"));
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
}
