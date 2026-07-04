
package com.smartlearnly.backend.test.service;

import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.test.dto.TestModel;
import com.smartlearnly.backend.test.entity.Test;
import com.smartlearnly.backend.test.entity.TestAttempt;
import com.smartlearnly.backend.test.repository.StudentTestAnswerRepository;
import com.smartlearnly.backend.test.repository.TestAttemptRepository;
import com.smartlearnly.backend.test.repository.TestRepository;
import jakarta.persistence.EntityNotFoundException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TestService {

    private static final Duration ACCESS_CODE_TTL = Duration.ofMinutes(5);
    private static final SecureRandom ACCESS_CODE_RANDOM = new SecureRandom();

    private final TestRepository testRepository;
    private final CurrentUserService currentUserService;
    private final TestAttemptRepository testAttemptRepository;
    private final StudentTestAnswerRepository studentTestAnswerRepository;

    public TestModel.Response createTest(
            TestModel.CreateRequest request) {

        Test test = new Test();

        test.setModuleId(request.getModuleId());
        test.setClassId(request.getClassId());
        test.setCourseId(request.getCourseId());
        test.setTitle(request.getTitle());
        test.setDescription(request.getDescription());
        test.setTestType(request.getTestType());
        test.setDurationMinutes(
                request.getDurationMinutes());
        test.setMaxAttempts(
                request.getMaxAttempts());
        test.setPassScore(
                request.getPassScore());
        test.setShuffleQuestions(
                request.getShuffleQuestions());
        test.setShuffleAnswers(
                request.getShuffleAnswers());
        test.setShowAnswersAfter(
                request.getShowAnswersAfter());
        test.setIsFlashtest(
                request.getIsFlashtest());
        ensureAccessCode(test);
        test.setCreatedBy(
                currentUserService.requireAuthenticatedUser().getId());

        Test saved = testRepository.save(test);

        return mapToResponse(saved);
    }

    public List<TestModel.Response> getAllTests() {

        List<Test> tests =
                testRepository.findAll();

        List<TestModel.Response> responses =
                new ArrayList<>();

        for (Test test : tests) {
            responses.add(mapToResponse(ensureAccessCode(test)));
        }

        return responses;
    }

    public List<TestModel.Response> getMyTests() {

        UUID currentUserId =
                currentUserService.requireAuthenticatedUser().getId();
        List<Test> tests =
                testRepository.findByCreatedBy(currentUserId);

        List<TestModel.Response> responses =
                new ArrayList<>();

        for (Test test : tests) {
            responses.add(mapToResponse(ensureAccessCode(test)));
        }

        return responses;
    }

    public TestModel.Response getTestById(UUID id) {

        Test test = testRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Test not found"));

        return mapToResponse(ensureAccessCode(test));
    }

    public TestModel.AccessCodeVerifyResponse verifyAccessCode(
            UUID id,
            TestModel.AccessCodeVerifyRequest request) {

        Test test = testRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Test not found"));
        test = ensureAccessCode(test);

        TestModel.AccessCodeVerifyResponse response =
                new TestModel.AccessCodeVerifyResponse();
        response.setValid(accessCodeMatches(test, request.getAccessCode()));
        response.setExpiresAt(test.getAccessCodeExpiresAt());
        return response;
    }

    @Transactional
    public TestModel.Response updateTest(
            UUID id,
            TestModel.UpdateRequest request) {

        Test test = testRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Test not found"));

        boolean isFlashTest = Boolean.TRUE.equals(test.getIsFlashtest()) ||
                Boolean.TRUE.equals(request.getIsFlashtest());
        if (isFlashTest) {
            boolean hasActiveAttempt = testAttemptRepository.existsActiveByTestId(id);
            if (hasActiveAttempt) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_RULE_VIOLATION,
                        "Cannot update this test while students are taking it");
            }
        }

        if (request.getModuleId() != null) test.setModuleId(request.getModuleId());
        if (request.getClassId() != null) test.setClassId(request.getClassId());
        if (request.getCourseId() != null) test.setCourseId(request.getCourseId());
        if (request.getTitle() != null) test.setTitle(request.getTitle());
        if (request.getDescription() != null) test.setDescription(request.getDescription());
        if (request.getTestType() != null) test.setTestType(request.getTestType());
        if (request.getDurationMinutes() != null) test.setDurationMinutes(request.getDurationMinutes());
        if (request.getMaxAttempts() != null) test.setMaxAttempts(request.getMaxAttempts());
        if (request.getPassScore() != null) test.setPassScore(request.getPassScore());
        if (request.getShuffleQuestions() != null) test.setShuffleQuestions(request.getShuffleQuestions());
        if (request.getShuffleAnswers() != null) test.setShuffleAnswers(request.getShuffleAnswers());
        if (request.getShowAnswersAfter() != null) test.setShowAnswersAfter(request.getShowAnswersAfter());
        if (request.getIsPublished() != null) test.setIsPublished(request.getIsPublished());
        if (request.getIsArchived() != null) test.setIsArchived(request.getIsArchived());
        if (request.getIsFlashtest() != null) test.setIsFlashtest(request.getIsFlashtest());
        ensureAccessCode(test);

        Test updated = testRepository.save(test);
        if (isFlashTest) {
            resetAttempts(id);
        }

        return mapToResponse(updated);
    }

    public void deleteTest(UUID id) {

        if (!testRepository.existsById(id)) {
            throw new EntityNotFoundException(
                    "Test not found");
        }

        testRepository.deleteById(id);
    }

    private void resetAttempts(UUID testId) {
        List<TestAttempt> attempts =
                testAttemptRepository.findByTestId(testId);
        if (attempts.isEmpty()) {
            return;
        }

        List<UUID> attemptIds = attempts.stream()
                .map(TestAttempt::getId)
                .toList();
        studentTestAnswerRepository.deleteByAttemptIds(attemptIds);
        testAttemptRepository.deleteAll(attempts);
    }

    private TestModel.Response mapToResponse(
            Test test) {

        TestModel.Response response =
                new TestModel.Response();

        response.setId(test.getId());
        response.setModuleId(test.getModuleId());
        response.setClassId(test.getClassId());
        response.setCourseId(test.getCourseId());
        response.setTitle(test.getTitle());
        response.setDescription(
                test.getDescription());
        response.setTestType(
                test.getTestType());
        response.setDurationMinutes(
                test.getDurationMinutes());
        response.setMaxAttempts(
                test.getMaxAttempts());
        response.setPassScore(
                test.getPassScore());
        response.setShuffleQuestions(
                test.getShuffleQuestions());
        response.setShuffleAnswers(
                test.getShuffleAnswers());
        response.setShowAnswersAfter(
                test.getShowAnswersAfter());
        response.setIsPublished(
                test.getIsPublished());
        response.setIsArchived(
                test.getIsArchived());
        response.setIsFlashtest(
                test.getIsFlashtest());
        response.setCreatedBy(
                test.getCreatedBy());
        response.setCreatedAt(
                test.getCreatedAt());
        response.setUpdatedAt(
                test.getUpdatedAt());
        response.setAccessCode(test.getAccessCode());
        response.setAccessCodeExpiresAt(test.getAccessCodeExpiresAt());

        return response;
    }

    private Test ensureAccessCode(Test test) {
        if (!Boolean.TRUE.equals(test.getIsFlashtest())) {
            return test;
        }
        Instant now = Instant.now();
        if (test.getAccessCode() == null ||
                test.getAccessCodeExpiresAt() == null ||
                !test.getAccessCodeExpiresAt().isAfter(now)) {
            test.setAccessCode(generateAccessCode());
            test.setAccessCodeExpiresAt(now.plus(ACCESS_CODE_TTL));
            return test.getId() == null ? test : testRepository.save(test);
        }
        return test;
    }

    public boolean accessCodeMatches(Test test, String accessCode) {
        if (!Boolean.TRUE.equals(test.getIsFlashtest())) {
            return true;
        }
        Test current = ensureAccessCode(test);
        String expected = current.getAccessCode();
        return expected != null &&
                expected.equals(String.valueOf(accessCode == null ? "" : accessCode).trim());
    }

    private String generateAccessCode() {
        return String.format("%06d", ACCESS_CODE_RANDOM.nextInt(1_000_000));
    }
}

