package com.smartlearnly.backend.question.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.question.dto.QuestionImportDtos;
import com.smartlearnly.backend.question.dto.QuestionModel;
import com.smartlearnly.backend.question.service.QuestionService;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/** Kiểm tra contract controller quản lý Question List theo một module trên URL. */
@ExtendWith(MockitoExtension.class)
class CourseModuleQuestionControllerTest {

    @Mock
    private QuestionService questionService;

    private CourseModuleQuestionController controller;
    private UUID courseId;
    private UUID moduleId;
    private UUID questionId;

    /** Khởi tạo controller cô lập và ID riêng cho từng test để tránh phụ thuộc dữ liệu dùng chung. */
    @BeforeEach
    void setUp() {
        controller = new CourseModuleQuestionController(questionService);
        courseId = UUID.randomUUID();
        moduleId = UUID.randomUUID();
        questionId = UUID.randomUUID();
    }

    /**
     * Xác nhận DTO công khai của luồng module-scoped không còn field courseId/moduleId.
     * Nhờ đó client không thể gửi một module khác với module đã có trên URL.
     */
    @Test
    void moduleScopedRequestDtos_doNotExposeCourseOrModuleFields() {
        assertThat(recordComponentNames(QuestionModel.ModuleCreateRequest.class))
                .doesNotContain("courseId", "moduleId");
        assertThat(recordComponentNames(QuestionModel.ModuleUpdateRequest.class))
                .doesNotContain("courseId", "moduleId");
        assertThat(recordComponentNames(QuestionImportDtos.ModuleImportRow.class))
                .doesNotContain("courseId", "moduleId");
    }

    /** Xác nhận bộ lọc danh sách được chuyển tới service cùng đúng course/module trên URL. */
    @Test
    void list_delegatesToModuleScopedService() {
        PageResponse<QuestionModel.Response> page = new PageResponse<>(List.of(), 1, 25, 0, 0);
        when(questionService.listByCourseModule(
                courseId, moduleId, "java", "multiple_choice", "draft", false, (short) 2, 1, 25
        )).thenReturn(page);

        var response = controller.list(
                courseId, moduleId, "java", "multiple_choice", "draft", false, (short) 2, 1, 25
        );

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isSameAs(page);
        verify(questionService).listByCourseModule(
                courseId, moduleId, "java", "multiple_choice", "draft", false, (short) 2, 1, 25
        );
    }

    /**
     * Xác nhận thao tác tạo dùng module từ URL và trả Location cũng chứa module đó.
     * Request body trong test chỉ có nội dung câu hỏi, không có field Module.
     */
    @Test
    void create_usesPathModuleAndReturnsModuleScopedLocation() {
        QuestionModel.ModuleCreateRequest request = createRequest();
        QuestionModel.Response created = response("What is Java?", "draft");
        when(questionService.createForCourse(courseId, moduleId, request)).thenReturn(created);

        var response = controller.create(courseId, moduleId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation().toString()).endsWith(
                "/api/v1/admin/courses/" + courseId
                        + "/modules/" + moduleId + "/questions/" + questionId
        );
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isSameAs(created);
        verify(questionService).createForCourse(courseId, moduleId, request);
    }

    /** Xác nhận update bị khóa vào module trên URL và không nhận module mới trong body. */
    @Test
    void update_usesPathModule() {
        QuestionModel.ModuleUpdateRequest request = updateRequest();
        QuestionModel.Response updated = response("Updated question", "pending_review");
        when(questionService.updateInCourse(courseId, moduleId, questionId, request)).thenReturn(updated);

        var response = controller.update(courseId, moduleId, questionId, request);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isSameAs(updated);
        verify(questionService).updateInCourse(courseId, moduleId, questionId, request);
    }

    /** Xác nhận archive luôn truyền đủ course/module/question để service kiểm tra truy cập chéo. */
    @Test
    void archive_usesPathModule() {
        var response = controller.archive(courseId, moduleId, questionId);

        assertThat(response.success()).isTrue();
        verify(questionService).archiveInCourse(courseId, moduleId, questionId);
    }

    /** Xác nhận batch import chỉ chứa dữ liệu câu hỏi và toàn bộ rows được gắn vào module trên URL. */
    @Test
    void importBatch_usesPathModule() {
        QuestionImportDtos.ModuleImportBatchRequest request = new QuestionImportDtos.ModuleImportBatchRequest(
                List.of(new QuestionImportDtos.ModuleImportRow(
                        2, "What is Java?", "single_choice", List.of("A", "B"),
                        "A", null, (short) 1, "remember", List.of(), List.of()
                )),
                "excel_import"
        );
        QuestionImportDtos.ImportBatchResponse imported =
                new QuestionImportDtos.ImportBatchResponse(1, 1, List.of(questionId), List.of());
        when(questionService.importBatchForCourse(courseId, moduleId, request)).thenReturn(imported);

        var response = controller.importBatch(courseId, moduleId, request);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isSameAs(imported);
        verify(questionService).importBatchForCourse(courseId, moduleId, request);
    }

    /** Xác nhận file CSV module-scoped không còn cột module_id dư thừa. */
    @Test
    void export_omitsModuleColumn() {
        when(questionService.listByCourseModule(
                courseId, moduleId, null, null, null, true, null, 0, 10_000
        )).thenReturn(new PageResponse<>(List.of(response("Question", "approved")), 0, 10_000, 1, 1));

        var response = controller.export(courseId, moduleId, null, null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentDisposition().getFilename())
                .isEqualTo("module-questions.csv");
        String csv = new String(response.getBody(), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFid,type,status,question_text\n");
        assertThat(csv).doesNotContain("module_id");
    }

    /** Lấy tên component của Java record để test chính xác contract JSON đầu vào. */
    private List<String> recordComponentNames(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents())
                .map(component -> component.getName())
                .toList();
    }

    /** Tạo request hợp lệ cho thao tác create mà không cần module trong body. */
    private QuestionModel.ModuleCreateRequest createRequest() {
        return new QuestionModel.ModuleCreateRequest(
                "What is Java?", "single_choice", "remember", (short) 2,
                null, "draft", answers()
        );
    }

    /** Tạo request hợp lệ cho thao tác update mà không thể thay đổi module. */
    private QuestionModel.ModuleUpdateRequest updateRequest() {
        return new QuestionModel.ModuleUpdateRequest(
                "Updated question", "single_choice", "understand", (short) 3,
                null, "pending_review", answers()
        );
    }

    /** Tạo hai đáp án tối thiểu cho câu hỏi single-choice. */
    private List<QuestionModel.AnswerRequest> answers() {
        return List.of(
                new QuestionModel.AnswerRequest(null, null, "A", true, null, 1, null),
                new QuestionModel.AnswerRequest(null, null, "B", false, null, 2, null)
        );
    }

    /** Tạo response giả lập để controller test không phụ thuộc repository hay database. */
    private QuestionModel.Response response(String questionText, String status) {
        return new QuestionModel.Response(
                questionId,
                questionId,
                courseId,
                moduleId,
                questionText,
                "single_choice",
                "remember",
                (short) 2,
                "Explanation",
                null,
                null,
                List.of(),
                false,
                null,
                status,
                2,
                List.of(),
                UUID.randomUUID(),
                null,
                null,
                null,
                null
        );
    }
}
