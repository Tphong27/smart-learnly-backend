
package com.smartlearnly.backend.test.service;

import com.smartlearnly.backend.test.dto.TestAttemptModel;
import com.smartlearnly.backend.test.entity.AttemptStatus;
import com.smartlearnly.backend.test.entity.TestAttempt;
import com.smartlearnly.backend.test.repository.TestAttemptRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TestAttemptService {

    private final TestAttemptRepository repository;

    public TestAttemptModel.Response startAttempt(
            TestAttemptModel.StartRequest request) {

        TestAttempt attempt = new TestAttempt();

        attempt.setTestId(request.getTestId());
        attempt.setStudentId(request.getStudentId());
        attempt.setAssignmentId(
                request.getAssignmentId());
        attempt.setStatus(AttemptStatus.IN_PROGRESS);

        TestAttempt saved = repository.save(attempt);

        return mapToResponse(saved);
    }

    public TestAttemptModel.Response submitAttempt(
            UUID id,
            TestAttemptModel.SubmitRequest request) {

        TestAttempt attempt = repository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Attempt not found"));

        attempt.setEndTime(
                request.getEndTime() != null
                        ? request.getEndTime()
                        : Instant.now());

        attempt.setScore(request.getScore());

        attempt.setStatus(request.getStatus());

        TestAttempt updated = repository.save(attempt);

        return mapToResponse(updated);
    }

    public List<TestAttemptModel.Response> getAttempts(
            UUID testId,
            UUID studentId) {

        List<TestAttempt> attempts =
                repository.findByTestIdAndStudentId(
                        testId,
                        studentId);

        List<TestAttemptModel.Response> responses =
                new ArrayList<>();

        for (TestAttempt attempt : attempts) {
            responses.add(mapToResponse(attempt));
        }

        return responses;
    }

    private TestAttemptModel.Response mapToResponse(
            TestAttempt attempt) {

        TestAttemptModel.Response response =
                new TestAttemptModel.Response();

        response.setId(attempt.getId());
        response.setTestId(attempt.getTestId());
        response.setStudentId(
                attempt.getStudentId());
        response.setStartTime(
                attempt.getStartTime());
        response.setEndTime(
                attempt.getEndTime());
        response.setScore(
                attempt.getScore());
        response.setStatus(
                attempt.getStatus());
        response.setCreatedAt(
                attempt.getCreatedAt());
        response.setAssignmentId(
                attempt.getAssignmentId());

        return response;
    }
}

