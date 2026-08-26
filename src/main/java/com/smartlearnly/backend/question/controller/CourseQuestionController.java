package com.smartlearnly.backend.question.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.question.dto.QuestionImportDtos;
import com.smartlearnly.backend.question.dto.QuestionMediaAttachmentResponse;
import com.smartlearnly.backend.question.dto.QuestionModel;
import com.smartlearnly.backend.question.service.QuestionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

@Validated
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SME', 'TMO', 'TRAINER')")
@RequestMapping("/api/v1/admin/courses/{courseId}/questions")
public class CourseQuestionController {
    private final QuestionService questionService;

    @GetMapping
    public ApiResponse<PageResponse<QuestionModel.Response>> list(
            @PathVariable UUID courseId,
            @RequestParam(required = false) UUID moduleId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "true") boolean includeArchived,
            @RequestParam(required = false) Short difficulty,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(
                "Questions loaded successfully",
                questionService.listByCourse(courseId, moduleId, search, type, status, includeArchived, difficulty, page, size)
        );
    }

    @GetMapping("/{questionId}")
    public ApiResponse<QuestionModel.Response> get(
            @PathVariable UUID courseId,
            @PathVariable UUID questionId
    ) {
        return ApiResponse.success("Question loaded successfully", questionService.getInCourse(courseId, questionId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SME', 'TRAINER')")
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
    @PreAuthorize("hasAnyRole('SME', 'TRAINER')")
    public ApiResponse<QuestionModel.Response> update(
            @PathVariable UUID courseId,
            @PathVariable UUID questionId,
            @Valid @RequestBody QuestionModel.UpdateRequest request
    ) {
        return ApiResponse.success("Question updated successfully", questionService.updateInCourse(courseId, questionId, request));
    }

    @DeleteMapping("/{questionId}")
    @PreAuthorize("hasAnyRole('SME', 'TRAINER')")
    public ApiResponse<Void> archive(
            @PathVariable UUID courseId,
            @PathVariable UUID questionId
    ) {
        questionService.archiveInCourse(courseId, questionId);
        return ApiResponse.success("Question archived successfully");
    }

    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('SME', 'TRAINER')")
    public ApiResponse<QuestionImportDtos.ImportBatchResponse> importBatch(
            @PathVariable UUID courseId,
            @Valid @RequestBody QuestionImportDtos.ImportBatchRequest request
    ) {
        return ApiResponse.success("Questions imported successfully", questionService.importBatchForCourse(courseId, request));
    }

    @GetMapping(value = "/export", produces = "text/csv")
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
                true,
                difficulty,
                0,
                10_000
        );
        byte[] csv = ("\uFEFF" + toCsv(page.items())).getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("course-questions.csv")
                        .build()
                        .toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }

    /** Xuat CSV theo template import, giu them id/status de phuc vu doi chieu du lieu. */
    private String toCsv(Iterable<QuestionModel.Response> questions) {
        StringBuilder builder = new StringBuilder(
                "id,status,Question text,Question type,Option A,Option B,Option C,Option D,Option E,Option F,Correct answer,Explanation,Module ID,Image files,Audio files\n"
        );
        for (QuestionModel.Response question : questions) {
            builder.append(csv(question.questionId()))
                    .append(',')
                    .append(csv(question.status()))
                    .append(',')
                    .append(csv(toPlainText(question.questionText())))
                    .append(',')
                    .append(csv(question.questionType()));
            for (int index = 0; index < 6; index += 1) {
                builder.append(',').append(csv(optionText(question, index)));
            }
            builder.append(',')
                    .append(csv(correctAnswer(question)))
                    .append(',')
                    .append(csv(toPlainText(question.explanation())))
                    .append(',')
                    .append(csv(question.moduleId()))
                    .append(',')
                    .append(csv(mediaUrls(question, "image", question.imageUrl())))
                    .append(',')
                    .append(csv(mediaUrls(question, "audio", question.audioUrl())))
                    .append('\n');
        }
        return builder.toString();
    }

    /** Lay noi dung option theo thu tu A-F va bo HTML neu dap an dang luu rich text. */
    private String optionText(QuestionModel.Response question, int index) {
        List<QuestionModel.AnswerResponse> answers = question.answers() == null
                ? List.of()
                : question.answers();
        if (index < 0 || index >= answers.size()) {
            return "";
        }
        return toPlainText(answers.get(index).answerText());
    }

    /** Doi danh sach dap an dung thanh format correct_answer ma import dang ho tro. */
    private String correctAnswer(QuestionModel.Response question) {
        List<QuestionModel.AnswerResponse> answers = question.answers() == null
                ? List.of()
                : question.answers();
        if ("true_false".equalsIgnoreCase(question.questionType())) {
            return answers.stream()
                    .filter(QuestionModel.AnswerResponse::correct)
                    .findFirst()
                    .map(answer -> toPlainText(answer.answerText()))
                    .orElse("");
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < answers.size(); index += 1) {
            if (!answers.get(index).correct()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(',');
            }
            builder.append((char) ('A' + index));
        }
        return builder.toString();
    }

    /** Ghep cac URL media cung loai bang dau cham phay dung voi parser import hien tai. */
    private String mediaUrls(QuestionModel.Response question, String mediaType, String fallbackUrl) {
        List<String> urls = question.mediaAttachments() == null
                ? List.of()
                : question.mediaAttachments().stream()
                        .filter(media -> mediaType.equalsIgnoreCase(media.mediaType()))
                        .map(QuestionMediaAttachmentResponse::mediaUrl)
                        .filter(url -> url != null && !url.isBlank())
                        .toList();
        if (!urls.isEmpty()) {
            return String.join("; ", urls);
        }
        return fallbackUrl == null ? "" : fallbackUrl;
    }

    private String toPlainText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String withoutBlockBreaks = value
                .replaceAll("(?i)<\\s*br\\s*/?\\s*>", "\n")
                .replaceAll("(?i)</\\s*(p|div|li|h[1-6]|tr)\\s*>", "\n");
        String withoutTags = withoutBlockBreaks.replaceAll("<[^>]+>", " ");
        String decoded = HtmlUtils.htmlUnescape(withoutTags).replace('\u00A0', ' ');
        return decoded.replaceAll("[ \\t\\u000B\\f\\r]+", " ")
                .replaceAll(" *\\n+ *", "\n")
                .trim();
    }

    private String csv(Object value) {
        if (value == null) {
            return "";
        }
        return "\"" + String.valueOf(value).replace("\"", "\"\"") + "\"";
    }
}
