
package com.smartlearnly.backend.assignment.submission.controller;

import com.smartlearnly.backend.assignment.submission.dto.AssignmentSubmissionModel;
import com.smartlearnly.backend.assignment.submission.service.AssignmentSubmissionService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

@RestController
@RequestMapping("/api/v1/submissions")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AssignmentSubmissionController {

    private final AssignmentSubmissionService submissionService;
    private static final Path SUBMISSION_UPLOAD_DIR =
            Path.of("uploads", "assignment-submissions").toAbsolutePath().normalize();
    private static final long MAX_FILE_SIZE = 25L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".pdf", ".doc", ".docx", ".ppt", ".pptx",
            ".png", ".jpg", ".jpeg", ".zip");
    private static final Set<String> ALLOWED_MEDIA_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "image/png",
            "image/jpeg",
            "application/zip",
            "application/x-zip-compressed");
    private static final Tika TIKA = new Tika();

    /** Bắt đầu lượt làm assignment của học viên hiện tại. */
    @PostMapping("/start")
    @PreAuthorize("hasRole('TRAINEE')")
    public ResponseEntity<AssignmentSubmissionModel.Response> startAssignment(
            @Valid @RequestBody AssignmentSubmissionModel.StartRequest request) {
        AssignmentSubmissionModel.Response response =
                submissionService.startAssignment(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /** Nộp nội dung hoặc file assignment của học viên hiện tại. */
    @PostMapping
    @PreAuthorize("hasRole('TRAINEE')")
    public ResponseEntity<AssignmentSubmissionModel.Response> submitAssignment(
            @Valid @RequestBody AssignmentSubmissionModel.CreateRequest request) {

        AssignmentSubmissionModel.Response response =
                submissionService.submitAssignment(request);

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /** Lưu file assignment sau khi kiểm tra kích thước, phần mở rộng và MIME thực tế. */
    @PostMapping(value = "/upload-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SME', 'TRAINER', 'TRAINEE')")
    public ResponseEntity<Map<String, String>> uploadSubmissionFile(
            @RequestPart("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size must not exceed 25 MB");
        }
        String originalName = file.getOriginalFilename() == null
                ? "submission.bin"
                : file.getOriginalFilename().replaceAll("[\\\\/]+", "_");
        String lowerName = originalName.toLowerCase(Locale.ROOT);
        if (ALLOWED_EXTENSIONS.stream().noneMatch(lowerName::endsWith)) {
            throw new IllegalArgumentException("Unsupported assignment file extension");
        }
        String detectedType = TIKA.detect(file.getInputStream(), originalName);
        if (!ALLOWED_MEDIA_TYPES.contains(detectedType)) {
            throw new IllegalArgumentException("Unsupported assignment file content");
        }
        String storedName = UUID.randomUUID() + "_" + originalName;
        Files.createDirectories(SUBMISSION_UPLOAD_DIR);
        Files.copy(file.getInputStream(), SUBMISSION_UPLOAD_DIR.resolve(storedName));
        String encoded = UriUtils.encodePathSegment(storedName, StandardCharsets.UTF_8);
        return ResponseEntity.ok(Map.of(
                "fileName", originalName,
                "fileUrl", "/api/v1/submissions/files/" + encoded));
    }

    /** Tải file đã được tham chiếu sau khi xác thực owner hoặc phạm vi lớp. */
    @GetMapping("/files/{storedName}")
    public ResponseEntity<Resource> downloadSubmissionFile(
            @PathVariable String storedName) throws IOException {
        String normalizedName = storedName.contains("+")
                ? storedName.replace("+", " ")
                : storedName;
        String encoded = UriUtils.encodePathSegment(normalizedName, StandardCharsets.UTF_8);
        submissionService.requireFileAccess("/api/v1/submissions/files/" + encoded);
        Path file = SUBMISSION_UPLOAD_DIR.resolve(normalizedName).normalize();
        if (!file.startsWith(SUBMISSION_UPLOAD_DIR) || !Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(normalizedName, StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(resource);
    }

    /** Cập nhật bài đang làm của chính học viên. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TRAINEE')")
    public ResponseEntity<AssignmentSubmissionModel.Response> updateSubmission(
            @PathVariable UUID id,
            @Valid @RequestBody AssignmentSubmissionModel.UpdateRequest request) {

        AssignmentSubmissionModel.Response response =
                submissionService.updateSubmission(id, request);

        return ResponseEntity.ok(response);
    }

    /** Chấm submission trong phạm vi assignment mà staff được quản lý. */
    @PutMapping("/{id}/grade")
    @PreAuthorize("hasAnyRole('SME', 'TRAINER')")
    public ResponseEntity<AssignmentSubmissionModel.Response> gradeSubmission(
            @PathVariable UUID id,
            @Valid @RequestBody AssignmentSubmissionModel.GradeRequest request) {

        AssignmentSubmissionModel.Response response =
                submissionService.gradeSubmission(id, request);

        return ResponseEntity.ok(response);
    }

    /** Sinh phản hồi AI cho submission trong phạm vi staff được quản lý. */
    @PostMapping("/{id}/ai-feedback")
    @PreAuthorize("hasAnyRole('SME', 'TRAINER')")
    public ResponseEntity<Map<String, String>> generateAiFeedback(
            @PathVariable UUID id) {
        return ResponseEntity.ok(submissionService.generateFeedback(id));
    }

    /** Liệt kê submission của assignment trong phạm vi staff được quản lý. */
    @GetMapping("/assignment/{assignmentId}")
    @PreAuthorize("hasAnyRole('TMO', 'SME', 'TRAINER')")
    public ResponseEntity<List<AssignmentSubmissionModel.Response>>
    getSubmissionsByAssignment(
            @PathVariable UUID assignmentId) {

        List<AssignmentSubmissionModel.Response> responses =
                submissionService.getSubmissionsByAssignment(assignmentId);

        return ResponseEntity.ok(responses);
    }

    /** Trả submission của một học viên cho owner hoặc staff có quyền. */
    @GetMapping("/assignment/{assignmentId}/student/{studentId}")
    public ResponseEntity<AssignmentSubmissionModel.Response>
    getSubmissionByAssignmentAndStudent(
            @PathVariable UUID assignmentId,
            @PathVariable UUID studentId) {

        AssignmentSubmissionModel.Response response =
                submissionService.getSubmissionByAssignmentAndStudent(
                        assignmentId,
                        studentId);

        return response == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(response);
    }
}

