
package com.smartlearnly.backend.test.definition.controller;

import com.smartlearnly.backend.test.definition.dto.TestModel;
import com.smartlearnly.backend.test.definition.service.TestService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tests")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class TestController {

    private final TestService testService;

    /** Tạo một đề mới trong course/lớp mà caller có quyền quản lý. */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO', 'SME', 'TRAINER')")
    public ResponseEntity<TestModel.Response> create(
            @Valid @RequestBody TestModel.CreateRequest request) {

        TestModel.Response response =
                testService.createTest(request);

        return new ResponseEntity<>(
                response,
                HttpStatus.CREATED);
    }

    /** Trả catalog đề đã xuất bản cho vai trò quản trị phù hợp. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO', 'SME')")
    public ResponseEntity<List<TestModel.Response>> getAll() {

        return ResponseEntity.ok(
                testService.getAllTests());
    }

    /** Trả đề trong phạm vi quản lý của nhân sự hiện tại. */
    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO', 'SME', 'TRAINER')")
    public ResponseEntity<List<TestModel.Response>> getMine(
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) UUID classId) {

        return ResponseEntity.ok(
                testService.getMyTests(courseId, classId));
    }

    /** Trả đề mà học viên hiện tại được phép làm. */
    @GetMapping("/available")
    @PreAuthorize("hasRole('TRAINEE')")
    public ResponseEntity<List<TestModel.Response>> getAvailable(
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) UUID classId,
            @RequestParam(required = false) Boolean isFlashtest) {

        return ResponseEntity.ok(
                testService.getAvailableTests(courseId, classId, isFlashtest));
    }

    /** Trả chi tiết đề nếu caller có quyền đọc đề đó. */
    @GetMapping("/{id}")
    public ResponseEntity<TestModel.Response> getById(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID classId) {

        return ResponseEntity.ok(
                testService.getTestById(id, classId));
    }

    /** Xác thực mã truy cập trước khi học viên bắt đầu làm đề. */
    @PostMapping("/{id}/access-code/verify")
    public ResponseEntity<TestModel.AccessCodeVerifyResponse> verifyAccessCode(
            @PathVariable UUID id,
            @RequestBody TestModel.AccessCodeVerifyRequest request) {

        return ResponseEntity.ok(
                testService.verifyAccessCode(id, request));
    }

    /** Cập nhật đề trong phạm vi caller được quản lý. */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO', 'SME', 'TRAINER')")
    public ResponseEntity<TestModel.Response> update(
            @PathVariable UUID id,
            @Valid @RequestBody TestModel.UpdateRequest request) {

        return ResponseEntity.ok(
                testService.updateTest(id, request));
    }

    /** Xóa đề cùng dữ liệu attempt phụ thuộc khi caller có quyền. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO', 'SME', 'TRAINER')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id) {

        testService.deleteTest(id);

        return ResponseEntity.noContent().build();
    }
}

