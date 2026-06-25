
package com.smartlearnly.backend.question.controller;

import com.smartlearnly.backend.question.dto.QuestionModel;
import com.smartlearnly.backend.question.service.QuestionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;

    @PostMapping
    public ResponseEntity<QuestionModel.Response> create(
            @Valid @RequestBody QuestionModel.CreateRequest request) {

        QuestionModel.Response response =
                questionService.create(request);

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<QuestionModel.Response>> getAll() {

        return ResponseEntity.ok(questionService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<QuestionModel.Response> getById(
            @PathVariable UUID id) {

        return ResponseEntity.ok(
                questionService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<QuestionModel.Response> update(
            @PathVariable UUID id,
            @Valid @RequestBody QuestionModel.UpdateRequest request) {

        return ResponseEntity.ok(
                questionService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id) {

        questionService.delete(id);

        return ResponseEntity.noContent().build();
    }
}

