package com.smartlearnly.backend.test.definition.service;

import com.smartlearnly.backend.test.definition.dto.TestQuestionModel;
import com.smartlearnly.backend.test.definition.mapping.TestQuestionMapper;
import com.smartlearnly.backend.test.entity.TestQuestion;
import com.smartlearnly.backend.test.entity.TestQuestion.TestQuestionId;
import com.smartlearnly.backend.test.repository.TestQuestionRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TestQuestionService {

    private final TestQuestionRepository repository;
    private final TestQuestionMapper mapper;

    /** Gắn một câu hỏi vào đề với thứ tự và số điểm được chỉ định. */
    public TestQuestionModel.Response addQuestionToTest(TestQuestionModel.AddRequest request) {
        TestQuestion entity = new TestQuestion();
        entity.setId(new TestQuestionId(request.getTestId(), request.getQuestionId()));
        entity.setOrderIndex(request.getOrderIndex());
        entity.setMarks(request.getMarks());
        return mapper.toResponse(repository.save(entity));
    }

    /** Trả mapping câu hỏi đầy đủ cho màn hình quản trị đề. */
    public List<TestQuestionModel.Response> getQuestionsByTest(UUID testId) {
        return repository.findByIdTestId(testId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    /** Trả câu hỏi theo DTO học viên, không kèm dữ liệu có thể lộ đáp án đúng. */
    public List<TestQuestionModel.LearnerResponse> getLearnerQuestionsByTest(UUID testId) {
        return repository.findByIdTestId(testId).stream()
                .map(mapper::toLearnerResponse)
                .toList();
    }

    /** Cập nhật thứ tự hoặc số điểm của một mapping câu hỏi trong đề. */
    public TestQuestionModel.Response updateTestQuestion(UUID testId, UUID questionId,
            TestQuestionModel.UpdateRequest request) {
        TestQuestionId id = new TestQuestionId(testId, questionId);
        TestQuestion entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Test question not found"));
        entity.setOrderIndex(request.getOrderIndex());
        entity.setMarks(request.getMarks());
        return mapper.toResponse(repository.save(entity));
    }

    /** Gỡ một câu hỏi khỏi đề sau khi xác nhận mapping tồn tại. */
    public void removeQuestionFromTest(UUID testId, UUID questionId) {
        TestQuestionId id = new TestQuestionId(testId, questionId);
        if (!repository.existsById(id)) {
            throw new EntityNotFoundException("Test question not found");
        }
        repository.deleteById(id);
    }
}
