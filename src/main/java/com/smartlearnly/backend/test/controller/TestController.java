
package com.smartlearnly.backend.test.controller;

import com.smartlearnly.backend.test.dto.TestModel;
import com.smartlearnly.backend.test.service.TestService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tests")
@RequiredArgsConstructor
public class TestController {

    private final TestService testService;

    @PostMapping
    public ResponseEntity<TestModel.Response> create(
            @Valid @RequestBody TestModel.CreateRequest request) {

        TestModel.Response response =
                testService.createTest(request);

        return new ResponseEntity<>(
                response,
                HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<TestModel.Response>> getAll() {

        return ResponseEntity.ok(
                testService.getAllTests());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TestModel.Response> getById(
            @PathVariable UUID id) {

        return ResponseEntity.ok(
                testService.getTestById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TestModel.Response> update(
            @PathVariable UUID id,
            @Valid @RequestBody TestModel.UpdateRequest request) {

        return ResponseEntity.ok(
                testService.updateTest(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id) {

        testService.deleteTest(id);

        return ResponseEntity.noContent().build();
    }
}

