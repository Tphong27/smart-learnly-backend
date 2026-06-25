
package com.smartlearnly.backend.test.service;

import com.smartlearnly.backend.test.dto.TestModel;
import com.smartlearnly.backend.test.entity.Test;
import com.smartlearnly.backend.test.repository.TestRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TestService {

    private final TestRepository testRepository;

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

        Test saved = testRepository.save(test);

        return mapToResponse(saved);
    }

    public List<TestModel.Response> getAllTests() {

        List<Test> tests =
                testRepository.findAll();

        List<TestModel.Response> responses =
                new ArrayList<>();

        for (Test test : tests) {
            responses.add(mapToResponse(test));
        }

        return responses;
    }

    public TestModel.Response getTestById(UUID id) {

        Test test = testRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Test not found"));

        return mapToResponse(test);
    }

    public TestModel.Response updateTest(
            UUID id,
            TestModel.UpdateRequest request) {

        Test test = testRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Test not found"));

        test.setTitle(request.getTitle());
        test.setDescription(
                request.getDescription());
        test.setTestType(
                request.getTestType());
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
        test.setIsPublished(
                request.getIsPublished());
        test.setIsArchived(
                request.getIsArchived());

        Test updated = testRepository.save(test);

        return mapToResponse(updated);
    }

    public void deleteTest(UUID id) {

        if (!testRepository.existsById(id)) {
            throw new EntityNotFoundException(
                    "Test not found");
        }

        testRepository.deleteById(id);
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
        response.setCreatedBy(
                test.getCreatedBy());
        response.setCreatedAt(
                test.getCreatedAt());
        response.setUpdatedAt(
                test.getUpdatedAt());

        return response;
    }
}

