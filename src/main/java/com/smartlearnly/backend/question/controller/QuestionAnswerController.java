
package com.smartlearnly.backend.question.controller;

import com.smartlearnly.backend.question.dto.QuestionAnswerModel;
import com.smartlearnly.backend.question.service.QuestionAnswerService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/question-answers")
@RequiredArgsConstructor
public class QuestionAnswerController {

    private final QuestionAnswerService questionAnswerService;

    @PostMapping
    public ResponseEntity<QuestionAnswerModel.Response> create(
            @Valid @RequestBody QuestionAnswerModel.CreateRequest request) {

        QuestionAnswerModel.Response response =
                questionAnswerService.create(request);

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/question/{questionId}")
    public ResponseEntity<List<QuestionAnswerModel.Response>>
    getAnswersByQuestion(
            @PathVariable UUID questionId) {

        return ResponseEntity.ok(
                questionAnswerService.getAnswersByQuestion(
                        questionId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<QuestionAnswerModel.Response> update(
            @PathVariable UUID id,
            @Valid @RequestBody QuestionAnswerModel.UpdateRequest request) {

        return ResponseEntity.ok(
                questionAnswerService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id) {

        questionAnswerService.delete(id);

        return ResponseEntity.noContent().build();
    }
}

