
package com.smartlearnly.backend.assignment.controller;

import com.smartlearnly.backend.assignment.dto.AssignmentSubmissionModel;
import com.smartlearnly.backend.assignment.service.AssignmentSubmissionService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/submissions")
@RequiredArgsConstructor
public class AssignmentSubmissionController {

    private final AssignmentSubmissionService submissionService;
    private static final Path SUBMISSION_UPLOAD_DIR =
            Path.of("uploads", "assignment-submissions");

    @PostMapping("/start")
    public ResponseEntity<AssignmentSubmissionModel.Response> startAssignment(
            @Valid @RequestBody AssignmentSubmissionModel.StartRequest request) {
        AssignmentSubmissionModel.Response response =
                submissionService.startAssignment(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping
    public ResponseEntity<AssignmentSubmissionModel.Response> submitAssignment(
            @Valid @RequestBody AssignmentSubmissionModel.CreateRequest request) {

        AssignmentSubmissionModel.Response response =
                submissionService.submitAssignment(request);

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping(value = "/upload-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadSubmissionFile(
            @RequestPart("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }
        String originalName = file.getOriginalFilename() == null
                ? "submission.bin"
                : file.getOriginalFilename().replaceAll("[\\\\/]+", "_");
        String storedName = UUID.randomUUID() + "_" + originalName;
        Files.createDirectories(SUBMISSION_UPLOAD_DIR);
        Files.copy(file.getInputStream(), SUBMISSION_UPLOAD_DIR.resolve(storedName));
        String encoded = URLEncoder.encode(storedName, StandardCharsets.UTF_8);
        return ResponseEntity.ok(Map.of(
                "fileName", originalName,
                "fileUrl", "/api/v1/submissions/files/" + encoded));
    }

    @GetMapping("/files/{storedName}")
    public ResponseEntity<ByteArrayResource> downloadSubmissionFile(
            @PathVariable String storedName) throws IOException {
        Path file = SUBMISSION_UPLOAD_DIR.resolve(storedName).normalize();
        if (!file.startsWith(SUBMISSION_UPLOAD_DIR) || !Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }
        ByteArrayResource resource = new ByteArrayResource(Files.readAllBytes(file));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(storedName, StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(resource);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AssignmentSubmissionModel.Response> updateSubmission(
            @PathVariable UUID id,
            @Valid @RequestBody AssignmentSubmissionModel.UpdateRequest request) {

        AssignmentSubmissionModel.Response response =
                submissionService.updateSubmission(id, request);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/grade")
    public ResponseEntity<AssignmentSubmissionModel.Response> gradeSubmission(
            @PathVariable UUID id,
            @Valid @RequestBody AssignmentSubmissionModel.GradeRequest request) {

        AssignmentSubmissionModel.Response response =
                submissionService.gradeSubmission(id, request);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/assignment/{assignmentId}")
    public ResponseEntity<List<AssignmentSubmissionModel.Response>>
    getSubmissionsByAssignment(
            @PathVariable UUID assignmentId) {

        List<AssignmentSubmissionModel.Response> responses =
                submissionService.getSubmissionsByAssignment(assignmentId);

        return ResponseEntity.ok(responses);
    }
}

