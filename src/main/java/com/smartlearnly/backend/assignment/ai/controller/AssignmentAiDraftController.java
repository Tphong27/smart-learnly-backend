package com.smartlearnly.backend.assignment.ai.controller;

import com.smartlearnly.backend.assignment.ai.dto.AssignmentAiDraftModel;
import com.smartlearnly.backend.assignment.ai.service.AssignmentAiDraftService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Nhận yêu cầu AI tạo bản nháp bài tập để giảng viên duyệt trước khi lưu. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/assignments")
@PreAuthorize("isAuthenticated()")
public class AssignmentAiDraftController {

    private final AssignmentAiDraftService assignmentAiDraftService;

    /**
     * Tạo bản nháp từ yêu cầu hoặc tệp nguồn; kết quả chỉ là bản nháp, không tự công bố bài tập.
     *
     * @return nội dung bản nháp AI để người dùng xem và chỉnh sửa
     */
    @PostMapping(value = "/ai-draft", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO', 'SME', 'TRAINER')")
    public ResponseEntity<AssignmentAiDraftModel.Response> generateAiDraft(
            @RequestParam String message,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) String currentTitle,
            @RequestParam(required = false) String currentDescription,
            @RequestParam(required = false) String sourceCacheKey,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        AssignmentAiDraftModel.Response response = assignmentAiDraftService.generateDraft(
                message,
                mode,
                currentTitle,
                currentDescription,
                sourceCacheKey,
                file);
        return ResponseEntity.ok(response);
    }
}
