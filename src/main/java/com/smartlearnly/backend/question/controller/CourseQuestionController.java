package com.smartlearnly.backend.question.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.question.dto.QuestionImportDtos;
import com.smartlearnly.backend.question.dto.QuestionModel;
import com.smartlearnly.backend.question.service.QuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SME', 'TMO', 'TRAINER')")
@RequestMapping("/api/v1/admin/courses/{courseId}/questions")
@Tag(name = "Admin Course Questions", description = "Course and module scoped question management APIs.")
@SecurityRequirement(name = "bearerAuth")
public class CourseQuestionController {
    private final QuestionService questionService;

    @GetMapping
    @Operation(summary = "List course questions")
    public ApiResponse<PageResponse<QuestionModel.Response>> list(
            @PathVariable UUID courseId,
            @RequestParam(required = false) UUID moduleId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Short difficulty,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(
                "Questions loaded successfully",
                questionService.listByCourse(courseId, moduleId, search, type, status, difficulty, page, size)
        );
    }

    @GetMapping("/{questionId}")
    @Operation(summary = "Get course question details")
    public ApiResponse<QuestionModel.Response> get(
            @PathVariable UUID courseId,
            @PathVariable UUID questionId
    ) {
        return ApiResponse.success("Question loaded successfully", questionService.getInCourse(courseId, questionId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SME')")
    @Operation(summary = "Create a course question")
    public ResponseEntity<ApiResponse<QuestionModel.Response>> create(
            @PathVariable UUID courseId,
            @Valid @RequestBody QuestionModel.CreateRequest request
    ) {
        QuestionModel.Response question = questionService.createForCourse(courseId, request);
        return ResponseEntity
                .created(URI.create("/api/v1/admin/courses/" + courseId + "/questions/" + question.questionId()))
                .body(ApiResponse.success("Question created successfully", question));
    }

    @PutMapping("/{questionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SME')")
    @Operation(summary = "Update a course question")
    public ApiResponse<QuestionModel.Response> update(
            @PathVariable UUID courseId,
            @PathVariable UUID questionId,
            @Valid @RequestBody QuestionModel.UpdateRequest request
    ) {
        return ApiResponse.success("Question updated successfully", questionService.updateInCourse(courseId, questionId, request));
    }

    @DeleteMapping("/{questionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SME')")
    @Operation(summary = "Archive a course question")
    public ApiResponse<Void> archive(
            @PathVariable UUID courseId,
            @PathVariable UUID questionId
    ) {
        questionService.archiveInCourse(courseId, questionId);
        return ApiResponse.success("Question archived successfully");
    }

    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('ADMIN', 'SME')")
    @Operation(summary = "Import course questions")
    public ApiResponse<QuestionImportDtos.ImportBatchResponse> importBatch(
            @PathVariable UUID courseId,
            @Valid @RequestBody QuestionImportDtos.ImportBatchRequest request
    ) {
        return ApiResponse.success("Questions imported successfully", questionService.importBatchForCourse(courseId, request));
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @Operation(summary = "Export course questions as CSV")
    public ResponseEntity<byte[]> export(
            @PathVariable UUID courseId,
            @RequestParam(required = false) UUID moduleId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Short difficulty
    ) {
        PageResponse<QuestionModel.Response> page = questionService.listByCourse(
                courseId,
                moduleId,
                search,
                type,
                status,
                difficulty,
                0,
                10_000
        );
        byte[] csv = toCsv(page.items()).getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("course-questions.csv")
                        .build()
                        .toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }

    private String toCsv(Iterable<QuestionModel.Response> questions) {
        StringBuilder builder = new StringBuilder("id,module_id,type,status,question_text\n");
        for (QuestionModel.Response question : questions) {
            builder.append(csv(question.questionId()))
                    .append(',')
                    .append(csv(question.moduleId()))
                    .append(',')
                    .append(csv(question.questionType()))
                    .append(',')
                    .append(csv(question.status()))
                    .append(',')
                    .append(csv(question.questionText()))
                    .append('\n');
        }
        return builder.toString();
    }

    private String csv(Object value) {
        if (value == null) {
            return "";
        }
        return "\"" + String.valueOf(value).replace("\"", "\"\"") + "\"";
    }
}
