package com.smartlearnly.backend.question.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.question.dto.QuestionImportDtos;
import com.smartlearnly.backend.question.dto.QuestionModel;
import com.smartlearnly.backend.question.service.QuestionService;
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
import org.springframework.web.util.HtmlUtils;

/** Cung cấp API quản lý Question List theo đúng một module được xác định từ URL. */
@Validated
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SME', 'TMO', 'TRAINER')")
@RequestMapping("/api/v1/admin/courses/{courseId}/modules/{moduleId}/questions")
public class CourseModuleQuestionController {
    private final QuestionService questionService;

    /** Trả danh sách đã phân trang của module hiện tại; không nhận bộ lọc module từ client. */
    @GetMapping
    public ApiResponse<PageResponse<QuestionModel.Response>> list(
            @PathVariable UUID courseId,
            @PathVariable UUID moduleId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "true") boolean includeArchived,
            @RequestParam(required = false) Short difficulty,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(
                "Module questions loaded successfully",
                questionService.listByCourseModule(
                        courseId, moduleId, search, type, status,
                        includeArchived, difficulty, page, size
                )
        );
    }

    /** Lấy một câu hỏi và trả 404 nếu câu hỏi không thuộc module trên URL. */
    @GetMapping("/{questionId}")
    public ApiResponse<QuestionModel.Response> get(
            @PathVariable UUID courseId,
            @PathVariable UUID moduleId,
            @PathVariable UUID questionId
    ) {
        return ApiResponse.success(
                "Question loaded successfully",
                questionService.getInCourse(courseId, moduleId, questionId)
        );
    }

    /** Tạo câu hỏi trong module trên URL; body chỉ chứa nội dung câu hỏi. */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SME')")
    public ResponseEntity<ApiResponse<QuestionModel.Response>> create(
            @PathVariable UUID courseId,
            @PathVariable UUID moduleId,
            @Valid @RequestBody QuestionModel.ModuleCreateRequest request
    ) {
        QuestionModel.Response question = questionService.createForCourse(courseId, moduleId, request);
        String location = "/api/v1/admin/courses/" + courseId
                + "/modules/" + moduleId + "/questions/" + question.questionId();
        return ResponseEntity.created(URI.create(location))
                .body(ApiResponse.success("Question created successfully", question));
    }

    /** Cập nhật câu hỏi nhưng không cho phép chuyển câu hỏi sang module khác. */
    @PutMapping("/{questionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SME')")
    public ApiResponse<QuestionModel.Response> update(
            @PathVariable UUID courseId,
            @PathVariable UUID moduleId,
            @PathVariable UUID questionId,
            @Valid @RequestBody QuestionModel.ModuleUpdateRequest request
    ) {
        return ApiResponse.success(
                "Question updated successfully",
                questionService.updateInCourse(courseId, moduleId, questionId, request)
        );
    }

    /** Lưu trữ câu hỏi trong đúng module hiện tại. */
    @DeleteMapping("/{questionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SME')")
    public ApiResponse<Void> archive(
            @PathVariable UUID courseId,
            @PathVariable UUID moduleId,
            @PathVariable UUID questionId
    ) {
        questionService.archiveInCourse(courseId, moduleId, questionId);
        return ApiResponse.success("Question archived successfully");
    }

    /** Import toàn bộ rows vào module trên URL, không đọc module từ file hoặc request body. */
    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('ADMIN', 'SME')")
    public ApiResponse<QuestionImportDtos.ImportBatchResponse> importBatch(
            @PathVariable UUID courseId,
            @PathVariable UUID moduleId,
            @Valid @RequestBody QuestionImportDtos.ModuleImportBatchRequest request
    ) {
        return ApiResponse.success(
                "Questions imported successfully",
                questionService.importBatchForCourse(courseId, moduleId, request)
        );
    }

    /** Xuất riêng câu hỏi của module và không lặp lại cột module trong file CSV. */
    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(
            @PathVariable UUID courseId,
            @PathVariable UUID moduleId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Short difficulty
    ) {
        PageResponse<QuestionModel.Response> page = questionService.listByCourseModule(
                courseId, moduleId, search, type, status, true, difficulty, 0, 10_000
        );
        byte[] csv = ("\uFEFF" + toCsv(page.items())).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("module-questions.csv")
                        .build()
                        .toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }

    /** Chuyển danh sách câu hỏi thành CSV dành cho đúng một module. */
    private String toCsv(Iterable<QuestionModel.Response> questions) {
        StringBuilder builder = new StringBuilder("id,type,status,question_text\n");
        for (QuestionModel.Response question : questions) {
            builder.append(csv(question.questionId()))
                    .append(',')
                    .append(csv(question.questionType()))
                    .append(',')
                    .append(csv(question.status()))
                    .append(',')
                    .append(csv(toPlainText(question.questionText())))
                    .append('\n');
        }
        return builder.toString();
    }

    /** Loại HTML khỏi nội dung rich text trước khi đưa vào CSV. */
    private String toPlainText(String value) {
        if (value == null || value.isBlank()) return "";
        String withoutBlockBreaks = value
                .replaceAll("(?i)<\\s*br\\s*/?\\s*>", "\n")
                .replaceAll("(?i)</\\s*(p|div|li|h[1-6]|tr)\\s*>", "\n");
        String withoutTags = withoutBlockBreaks.replaceAll("<[^>]+>", " ");
        String decoded = HtmlUtils.htmlUnescape(withoutTags).replace('\u00A0', ' ');
        return decoded.replaceAll("[ \\t\\u000B\\f\\r]+", " ")
                .replaceAll(" *\\n+ *", "\n")
                .trim();
    }

    /** Escape một giá trị CSV theo chuẩn dấu ngoặc kép. */
    private String csv(Object value) {
        if (value == null) return "";
        return "\"" + String.valueOf(value).replace("\"", "\"\"") + "\"";
    }
}
