package com.smartlearnly.backend.test.definition.mapping;

import com.smartlearnly.backend.question.dto.QuestionModel;
import com.smartlearnly.backend.question.entity.Question;
import com.smartlearnly.backend.question.entity.QuestionAnswer;
import com.smartlearnly.backend.question.entity.QuestionAnswerMediaAttachment;
import com.smartlearnly.backend.question.entity.QuestionMediaAttachment;
import com.smartlearnly.backend.question.entity.QuestionMediaType;
import com.smartlearnly.backend.question.repository.QuestionAnswerMediaAttachmentRepository;
import com.smartlearnly.backend.question.repository.QuestionAnswerRepository;
import com.smartlearnly.backend.question.repository.QuestionMediaAttachmentRepository;
import com.smartlearnly.backend.question.repository.QuestionRepository;
import com.smartlearnly.backend.test.definition.dto.TestQuestionModel;
import com.smartlearnly.backend.test.entity.TestQuestion;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Maps TestQuestion entities to DTOs for admin and learner responses.
 */
@Component
public class TestQuestionMapper {

    private final QuestionRepository questionRepository;
    private final QuestionAnswerRepository answerRepository;
    private final QuestionMediaAttachmentRepository mediaAttachmentRepository;
    private final QuestionAnswerMediaAttachmentRepository answerMediaAttachmentRepository;

    public TestQuestionMapper(
            QuestionRepository questionRepository,
            QuestionAnswerRepository answerRepository,
            QuestionMediaAttachmentRepository mediaAttachmentRepository,
            QuestionAnswerMediaAttachmentRepository answerMediaAttachmentRepository) {
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.mediaAttachmentRepository = mediaAttachmentRepository;
        this.answerMediaAttachmentRepository = answerMediaAttachmentRepository;
    }

    /**
     * Maps a TestQuestion entity to an admin response with full details.
     */
    public TestQuestionModel.Response toResponse(TestQuestion entity) {
        TestQuestionModel.Response response = new TestQuestionModel.Response();
        populateBaseResponse(response, entity);
        questionRepository.findById(entity.getId().getQuestionId())
                .ifPresent(question -> appendQuestionDetails(response, question));
        return response;
    }

    /**
     * Maps a TestQuestion entity to a learner-safe response without correct answers.
     */
    public TestQuestionModel.LearnerResponse toLearnerResponse(TestQuestion entity) {
        TestQuestionModel.LearnerResponse response = new TestQuestionModel.LearnerResponse();
        response.setTestId(entity.getId().getTestId());
        response.setQuestionId(entity.getId().getQuestionId());
        response.setOrderIndex(entity.getOrderIndex());
        response.setMarks(entity.getMarks());
        questionRepository.findById(entity.getId().getQuestionId())
                .ifPresent(question -> appendLearnerQuestionDetails(response, question));
        return response;
    }

    private void populateBaseResponse(TestQuestionModel.Response response, TestQuestion entity) {
        response.setTestId(entity.getId().getTestId());
        response.setQuestionId(entity.getId().getQuestionId());
        response.setOrderIndex(entity.getOrderIndex());
        response.setMarks(entity.getMarks());
    }

    private void appendQuestionDetails(TestQuestionModel.Response response, Question question) {
        response.setQuestionText(question.getQuestionText());
        response.setImageUrl(primaryMediaUrl(question, QuestionMediaType.IMAGE));
        response.setAudioUrl(primaryMediaUrl(question, QuestionMediaType.AUDIO));
        response.setQuestionType(question.getQuestionType() == null
                ? null
                : question.getQuestionType().name().toLowerCase());
        response.setAnswers(answerRepository
                .findByQuestionIdOrderByOrderIndexAsc(question.getId())
                .stream()
                .map(answer -> new QuestionModel.AnswerResponse(
                        answer.getId(),
                        answer.getId(),
                        answer.getAnswerText(),
                        Boolean.TRUE.equals(answer.getIsCorrect()),
                        Boolean.TRUE.equals(answer.getIsCorrect()),
                        answer.getOrderIndex() == null ? 0 : answer.getOrderIndex(),
                        answer.getOrderIndex() == null ? 0 : answer.getOrderIndex(),
                        List.of()))
                .toList());
    }

    private void appendLearnerQuestionDetails(TestQuestionModel.LearnerResponse response, Question question) {
        response.setQuestionText(question.getQuestionText());
        response.setImageUrl(primaryMediaUrl(question, QuestionMediaType.IMAGE));
        response.setAudioUrl(primaryMediaUrl(question, QuestionMediaType.AUDIO));
        response.setQuestionType(question.getQuestionType() == null
                ? null
                : question.getQuestionType().name().toLowerCase());

        List<QuestionAnswer> answerEntities = answerRepository.findByQuestionIdOrderByOrderIndexAsc(question.getId());
        Map<UUID, List<QuestionAnswerMediaAttachment>> mediaByAnswer = answerEntities.isEmpty()
                ? Map.of()
                : answerMediaAttachmentRepository.findByAnswerIdIn(
                        answerEntities.stream()
                                .map(QuestionAnswer::getId)
                                .toList())
                        .stream()
                        .collect(Collectors.groupingBy(QuestionAnswerMediaAttachment::getAnswerId));

        response.setAnswers(answerEntities.stream()
                .map(answer -> {
                    TestQuestionModel.LearnerAnswerResponse learnerAnswer = new TestQuestionModel.LearnerAnswerResponse();
                    Integer order = answer.getOrderIndex() == null ? 0 : answer.getOrderIndex();
                    learnerAnswer.setAnswerId(answer.getId());
                    learnerAnswer.setId(answer.getId());
                    learnerAnswer.setAnswerText(answer.getAnswerText());
                    learnerAnswer.setDisplayOrder(order);
                    learnerAnswer.setOrderIndex(order);
                    learnerAnswer.setMedia(mediaByAnswer.getOrDefault(answer.getId(), List.of()).stream()
                            .map(this::toAnswerMediaResponse)
                            .toList());
                    return learnerAnswer;
                })
                .toList());
    }

    private com.smartlearnly.backend.question.dto.QuestionAnswerMediaResponse toAnswerMediaResponse(
            QuestionAnswerMediaAttachment media) {
        return new com.smartlearnly.backend.question.dto.QuestionAnswerMediaResponse(
                media.getId(),
                media.getAnswerId(),
                media.getMediaType() == null ? null : media.getMediaType().name().toLowerCase(Locale.ROOT),
                media.getMediaUrl(),
                media.getObjectKey(),
                media.getBucket(),
                media.getContentType(),
                media.getFileSize() == null ? 0 : media.getFileSize(),
                media.getOriginalFileName(),
                media.getDisplayOrder() == null ? 0 : media.getDisplayOrder(),
                media.getImportSource(),
                media.getCreatedAt(),
                media.getUpdatedAt());
    }

    private String primaryMediaUrl(Question question, QuestionMediaType mediaType) {
        return mediaAttachmentRepository
                .findFirstByQuestionIdAndMediaTypeOrderByDisplayOrderAsc(question.getId(), mediaType)
                .map(QuestionMediaAttachment::getMediaUrl)
                .orElse(null);
    }
}
