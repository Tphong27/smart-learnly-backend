package com.smartlearnly.backend.videoai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.videoai.config.VideoAiGenerationProperties;
import com.smartlearnly.backend.videoai.dto.VideoAiDtos.GeneratedSummary;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Unit test cho {@link GeminiVideoSummaryService}.
 *
 * <p>Mỗi method test chỉ kiểm tra một trường hợp. Các comment Given - When -
 * Then mô tả rõ input, cách gọi service và output mong muốn.
 */
class GeminiVideoSummaryServiceTest {

    /**
     * Mục đích: kiểm tra luồng thành công với đúng response chuẩn của Gemini.
     *
     * <p>Input: language = {@code vi}, transcript là nội dung bài học React.
     * Gemini trả đúng 3 đoạn overview, một tiêu đề và 3 key takeaways.
     * Expected output: {@link GeneratedSummary} có cấu trúc để frontend render
     * thành các đoạn văn và danh sách bullet.
     */
    @Test
    void generateSummaryFromTranscript_returnsStructuredSummary_whenGeminiResponseIsValid()
            throws Exception {
        // GIVEN: Service dùng một model duy nhất; prefix "models/" sẽ được bỏ.
        TestContext context = context("models/gemini-test");
        GeneratedSummary providerSummary = new GeneratedSummary(
                List.of(
                        " Tổng quan về React state. ",
                        "State thay đổi khi người dùng tương tác.",
                        "Người học có thể dùng updater function."),
                "Điểm chính",
                List.of(
                        "- State lưu dữ liệu thay đổi",
                        "* Không sửa state trực tiếp",
                        "• Dùng updater function"));

        // GIVEN: Gemini trả response đúng đường dẫn candidates[0].content.parts[0].text.
        String providerResponse =
                providerResponse(context, providerSummary);
        context.server()
                .expect(requestTo(
                        "https://gemini.example.test/v1beta/models/gemini-test:generateContent"))
                .andExpect(header("x-goog-api-key", "gemini-test-key"))
                .andExpect(jsonPath(
                        "$.contents[0].parts[0].text",
                        containsString("Write in the transcript language: vi")))
                .andExpect(jsonPath(
                        "$.contents[0].parts[0].text",
                        containsString("Bài học giải thích React state")))
                .andExpect(jsonPath(
                        "$.generationConfig.responseJsonSchema"
                                + ".properties.overviewParagraphs.maxItems")
                        .value(3))
                .andExpect(jsonPath(
                        "$.generationConfig.responseJsonSchema"
                                + ".properties.keyTakeaways.maxItems")
                        .value(5))
                .andRespond(withSuccess(
                        providerResponse,
                        MediaType.APPLICATION_JSON));

        // WHEN: Service gửi transcript cho Gemini để tạo summary.
        GeneratedSummary actual = context.service().generateSummaryFromTranscript(
                "vi",
                "Bài học giải thích React state");

        // THEN: Dữ liệu được trim và ký tự bullet thừa được loại bỏ.
        assertThat(actual.overviewParagraphs()).containsExactly(
                "Tổng quan về React state.",
                "State thay đổi khi người dùng tương tác.",
                "Người học có thể dùng updater function.");
        assertThat(actual.keyTakeawaysTitle()).isEqualTo("Điểm chính");
        assertThat(actual.keyTakeaways()).containsExactly(
                "State lưu dữ liệu thay đổi",
                "Không sửa state trực tiếp",
                "Dùng updater function");
        context.server().verify();
    }

    /**
     * Mục đích: kiểm tra language không có giá trị.
     *
     * <p>Input: language = null, transcript hợp lệ.
     * Expected output: prompt yêu cầu Gemini dùng ngôn ngữ được phát hiện,
     * thay vì chèn chữ {@code null} vào prompt.
     */
    @Test
    void generateSummaryFromTranscript_usesDetectedLanguageInstruction_whenLanguageIsNull()
            throws Exception {
        // GIVEN: Gemini sẽ trả một summary hợp lệ.
        TestContext context = context("gemini-test");
        context.server()
                .expect(requestTo(containsString(
                        "/models/gemini-test:generateContent")))
                .andExpect(jsonPath(
                        "$.contents[0].parts[0].text",
                        containsString(
                                "Write in the transcript language: "
                                        + "the detected language")))
                .andRespond(withSuccess(
                        providerResponse(context, validSummary()),
                        MediaType.APPLICATION_JSON));

        // WHEN: Service được gọi với language bằng null.
        GeneratedSummary actual =
                context.service().generateSummaryFromTranscript(null, "Lesson transcript");

        // THEN: Summary vẫn được tạo thành công.
        assertThat(actual).isEqualTo(validSummary());
        context.server().verify();
    }

    /**
     * Mục đích: không gọi Gemini khi tính năng generation bị tắt.
     *
     * <p>Input: enabled = false, API key và transcript vẫn hợp lệ.
     * Expected output: EXTERNAL_SERVICE_UNAVAILABLE.
     */
    @Test
    void generateSummaryFromTranscript_throwsUnavailable_whenServiceIsDisabled() {
        // GIVEN: Cấu hình chủ động tắt Gemini generation.
        VideoAiGenerationProperties properties = properties();
        properties.setEnabled(false);

        // WHEN + THEN: Service từ chối trước khi tạo HTTP request.
        assertErrorCode(
                () -> new GeminiVideoSummaryService(properties)
                        .generateSummaryFromTranscript("en", "Lesson transcript"),
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
    }

    /**
     * Mục đích: không gọi Gemini khi API key chưa được cấu hình.
     *
     * <p>Input: apiKey = null. Expected output:
     * EXTERNAL_SERVICE_UNAVAILABLE.
     */
    @Test
    void generateSummaryFromTranscript_throwsUnavailable_whenApiKeyIsNull() {
        // GIVEN: API key không tồn tại.
        VideoAiGenerationProperties properties = properties();
        properties.setApiKey(null);

        // WHEN + THEN: Cấu hình thiếu phải bị từ chối.
        assertErrorCode(
                () -> new GeminiVideoSummaryService(properties)
                        .generateSummaryFromTranscript("en", "Lesson transcript"),
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
    }

    /**
     * Mục đích: không chấp nhận API key chỉ chứa khoảng trắng.
     *
     * <p>Input: apiKey = "   ". Expected output:
     * EXTERNAL_SERVICE_UNAVAILABLE.
     */
    @Test
    void generateSummaryFromTranscript_throwsUnavailable_whenApiKeyIsBlank() {
        // GIVEN: API key không có ký tự sử dụng được.
        VideoAiGenerationProperties properties = properties();
        properties.setApiKey("   ");

        // WHEN + THEN: Chuỗi blank được xem là chưa cấu hình.
        assertErrorCode(
                () -> new GeminiVideoSummaryService(properties)
                        .generateSummaryFromTranscript("en", "Lesson transcript"),
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
    }

    /**
     * Mục đích: yêu cầu tên model cụ thể vì không còn fallback model.
     *
     * <p>Input: model = null. Expected output:
     * EXTERNAL_SERVICE_UNAVAILABLE và không tự chọn model dự phòng.
     */
    @Test
    void generateSummaryFromTranscript_throwsUnavailable_whenModelIsNull() {
        // GIVEN: Không có model để tạo URL gọi Gemini.
        VideoAiGenerationProperties properties = properties();
        properties.setModel(null);

        // WHEN + THEN: Service báo cấu hình không khả dụng.
        assertErrorCode(
                () -> new GeminiVideoSummaryService(properties)
                        .generateSummaryFromTranscript("en", "Lesson transcript"),
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
    }

    /**
     * Mục đích: transcript là tham số bắt buộc của method generate.
     *
     * <p>Input: transcript = null. Expected output: INVALID_REQUEST.
     */
    @Test
    void generateSummaryFromTranscript_throwsInvalidRequest_whenTranscriptIsNull() {
        // GIVEN: Service đã có cấu hình Gemini hợp lệ.
        TestContext context = context("gemini-test");

        // WHEN + THEN: Không có transcript nên không thể tạo summary.
        assertErrorCode(
                () -> context.service().generateSummaryFromTranscript("en", null),
                ErrorCode.INVALID_REQUEST);
    }

    /**
     * Mục đích: transcript chỉ có khoảng trắng không phải nội dung hợp lệ.
     *
     * <p>Input: transcript = " \n ". Expected output: INVALID_REQUEST.
     */
    @Test
    void generateSummaryFromTranscript_throwsInvalidRequest_whenTranscriptIsBlank() {
        // GIVEN: Service đã có cấu hình Gemini hợp lệ.
        TestContext context = context("gemini-test");

        // WHEN + THEN: Transcript không có chữ phải bị từ chối.
        assertErrorCode(
                () -> context.service().generateSummaryFromTranscript("en", " \n "),
                ErrorCode.INVALID_REQUEST);
    }

    /**
     * Mục đích: một lỗi HTTP từ model phải trả lỗi ngay, không gọi model khác.
     *
     * <p>Input: Gemini trả HTTP 400. Expected output:
     * EXTERNAL_SERVICE_UNAVAILABLE và mock server chỉ nhận đúng một request.
     */
    @Test
    void generateSummaryFromTranscript_throwsUnavailable_withoutFallback_whenGeminiReturnsHttpError() {
        // GIVEN: Model duy nhất trả BAD_REQUEST.
        TestContext context = context("gemini-only");
        context.server()
                .expect(requestTo(containsString(
                        "/models/gemini-only:generateContent")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        // WHEN + THEN: Backend không thử một model dự phòng.
        assertErrorCode(
                () -> context.service().generateSummaryFromTranscript("en", "Lesson transcript"),
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        context.server().verify();
    }

    /**
     * Mục đích: từ chối response không có body.
     *
     * <p>Input: Gemini trả HTTP 204 No Content.
     * Expected output: EXTERNAL_SERVICE_UNAVAILABLE.
     */
    @Test
    void generateSummaryFromTranscript_throwsUnavailable_whenGeminiReturnsNoContent() {
        // GIVEN: HTTP request thành công nhưng không có JSON response.
        TestContext context = context("gemini-test");
        context.server()
                .expect(requestTo(containsString(
                        "/models/gemini-test:generateContent")))
                .andRespond(withNoContent());

        // WHEN + THEN: Không có summary để trả cho frontend.
        assertErrorCode(
                () -> context.service().generateSummaryFromTranscript("en", "Lesson transcript"),
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        context.server().verify();
    }

    /**
     * Mục đích: chỉ đọc response theo cấu trúc chuẩn của Gemini.
     *
     * <p>Input: JSON chỉ có field top-level {@code text}, không có
     * {@code candidates[0].content.parts[0].text}.
     * Expected output: EXTERNAL_SERVICE_UNAVAILABLE.
     */
    @Test
    void generateSummaryFromTranscript_throwsUnavailable_whenProviderEnvelopeIsNotStandard()
            throws Exception {
        // GIVEN: Nội dung summary đúng nhưng nằm sai vị trí trong response.
        TestContext context = context("gemini-test");
        String response = context.objectMapper().writeValueAsString(Map.of(
                "text",
                context.objectMapper().writeValueAsString(validSummary())));
        context.server()
                .expect(requestTo(containsString(
                        "/models/gemini-test:generateContent")))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        // WHEN + THEN: Service không tìm kiếm text đệ quy ở vị trí tùy ý.
        assertErrorCode(
                () -> context.service().generateSummaryFromTranscript("en", "Lesson transcript"),
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        context.server().verify();
    }

    /**
     * Mục đích: xác nhận định dạng legacy chỉ có field summary đã bị loại bỏ.
     *
     * <p>Input từ Gemini: {@code {"summary":"Old summary"}}.
     * Expected output: EXTERNAL_SERVICE_UNAVAILABLE, vì frontend cần object
     * overviewParagraphs/keyTakeaways để render nhất quán.
     */
    @Test
    void generateSummaryFromTranscript_throwsUnavailable_whenGeminiReturnsLegacySummary()
            throws Exception {
        // GIVEN: Response envelope đúng nhưng text chứa định dạng summary cũ.
        TestContext context = context("gemini-test");
        String legacyOutput = context.objectMapper().writeValueAsString(
                Map.of("summary", "Old summary"));
        expectOutput(context, legacyOutput);

        // WHEN + THEN: Legacy summary không còn được chấp nhận.
        assertErrorCode(
                () -> context.service().generateSummaryFromTranscript("en", "Lesson transcript"),
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        context.server().verify();
    }

    /**
     * Mục đích: từ chối nội dung không phải JSON.
     *
     * <p>Input text từ Gemini = "not-json".
     * Expected output: EXTERNAL_SERVICE_UNAVAILABLE.
     */
    @Test
    void generateSummaryFromTranscript_throwsUnavailable_whenSummaryJsonIsMalformed()
            throws Exception {
        // GIVEN: Provider envelope đúng nhưng phần text không parse được.
        TestContext context = context("gemini-test");
        expectOutput(context, "not-json");

        // WHEN + THEN: JSON lỗi không được truyền thẳng tới frontend.
        assertErrorCode(
                () -> context.service().generateSummaryFromTranscript("en", "Lesson transcript"),
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        context.server().verify();
    }

    /**
     * Mục đích: summary phải có đúng 3 overview paragraphs.
     *
     * <p>Input: chỉ có 2 paragraphs. Expected output:
     * EXTERNAL_SERVICE_UNAVAILABLE.
     */
    @Test
    void generateSummaryFromTranscript_throwsUnavailable_whenOverviewHasOnlyTwoParagraphs()
            throws Exception {
        // GIVEN: Các field tồn tại nhưng số paragraph không đúng contract.
        TestContext context = context("gemini-test");
        GeneratedSummary invalid = new GeneratedSummary(
                List.of("Paragraph one", "Paragraph two"),
                "Key takeaways",
                List.of("One", "Two", "Three"));
        expectOutput(
                context,
                context.objectMapper().writeValueAsString(invalid));

        // WHEN + THEN: Output thiếu paragraph phải bị từ chối.
        assertErrorCode(
                () -> context.service().generateSummaryFromTranscript("en", "Lesson transcript"),
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        context.server().verify();
    }

    /**
     * Mục đích: keyTakeawaysTitle không được để trống.
     *
     * <p>Input: title = "   ". Expected output:
     * EXTERNAL_SERVICE_UNAVAILABLE.
     */
    @Test
    void generateSummaryFromTranscript_throwsUnavailable_whenTakeawayTitleIsBlank()
            throws Exception {
        // GIVEN: Paragraph và takeaways hợp lệ nhưng title chỉ có khoảng trắng.
        TestContext context = context("gemini-test");
        GeneratedSummary invalid = new GeneratedSummary(
                List.of("One", "Two", "Three"),
                "   ",
                List.of("One", "Two", "Three"));
        expectOutput(
                context,
                context.objectMapper().writeValueAsString(invalid));

        // WHEN + THEN: Frontend không nhận một heading rỗng.
        assertErrorCode(
                () -> context.service().generateSummaryFromTranscript("en", "Lesson transcript"),
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        context.server().verify();
    }

    /**
     * Mục đích: summary phải có ít nhất 3 key takeaways.
     *
     * <p>Input: chỉ có 2 takeaways. Expected output:
     * EXTERNAL_SERVICE_UNAVAILABLE.
     */
    @Test
    void generateSummaryFromTranscript_throwsUnavailable_whenThereAreOnlyTwoTakeaways()
            throws Exception {
        // GIVEN: Gemini trả ít hơn boundary tối thiểu.
        TestContext context = context("gemini-test");
        GeneratedSummary invalid = new GeneratedSummary(
                List.of("One", "Two", "Three"),
                "Key takeaways",
                List.of("One", "Two"));
        expectOutput(
                context,
                context.objectMapper().writeValueAsString(invalid));

        // WHEN + THEN: Output ít hơn 3 takeaways phải bị từ chối.
        assertErrorCode(
                () -> context.service().generateSummaryFromTranscript("en", "Lesson transcript"),
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        context.server().verify();
    }

    /**
     * Mục đích: summary không được có nhiều hơn 5 key takeaways.
     *
     * <p>Input: 6 takeaways. Expected output:
     * EXTERNAL_SERVICE_UNAVAILABLE.
     */
    @Test
    void generateSummaryFromTranscript_throwsUnavailable_whenThereAreSixTakeaways()
            throws Exception {
        // GIVEN: Gemini trả nhiều hơn boundary tối đa.
        TestContext context = context("gemini-test");
        GeneratedSummary invalid = new GeneratedSummary(
                List.of("One", "Two", "Three"),
                "Key takeaways",
                List.of("One", "Two", "Three", "Four", "Five", "Six"));
        expectOutput(
                context,
                context.objectMapper().writeValueAsString(invalid));

        // WHEN + THEN: Output nhiều hơn 5 takeaways phải bị từ chối.
        assertErrorCode(
                () -> context.service().generateSummaryFromTranscript("en", "Lesson transcript"),
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        context.server().verify();
    }

    /**
     * Mục đích: ngăn provider trả nội dung quá lớn cho frontend.
     *
     * <p>Input: tổng số ký tự lớn hơn 50.000.
     * Expected output: EXTERNAL_SERVICE_UNAVAILABLE.
     */
    @Test
    void generateSummaryFromTranscript_throwsUnavailable_whenSummaryIsOversized()
            throws Exception {
        // GIVEN: Cấu trúc đúng nhưng paragraph đầu tiên vượt giới hạn.
        TestContext context = context("gemini-test");
        GeneratedSummary invalid = new GeneratedSummary(
                List.of("x".repeat(50_001), "Two", "Three"),
                "Key takeaways",
                List.of("One", "Two", "Three"));
        expectOutput(
                context,
                context.objectMapper().writeValueAsString(invalid));

        // WHEN + THEN: Service không trả nội dung quá lớn về client.
        assertErrorCode(
                () -> context.service().generateSummaryFromTranscript("en", "Lesson transcript"),
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        context.server().verify();
    }

    private TestContext context(String model) {
        VideoAiGenerationProperties properties = properties();
        properties.setModel(model);
        ObjectMapper objectMapper =
                new ObjectMapper().findAndRegisterModules();
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getApiBaseUrl());
        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();
        return new TestContext(
                new GeminiVideoSummaryService(
                        properties,
                        objectMapper,
                        builder.build()),
                objectMapper,
                server);
    }

    private VideoAiGenerationProperties properties() {
        VideoAiGenerationProperties properties =
                new VideoAiGenerationProperties();
        properties.setEnabled(true);
        properties.setApiKey("gemini-test-key");
        properties.setApiBaseUrl(
                "https://gemini.example.test/v1beta");
        properties.setModel("gemini-test");
        return properties;
    }

    private GeneratedSummary validSummary() {
        return new GeneratedSummary(
                List.of(
                        "First paragraph",
                        "Second paragraph",
                        "Third paragraph"),
                "Key takeaways",
                List.of("First", "Second", "Third"));
    }

    private String providerResponse(
            TestContext context,
            GeneratedSummary summary) throws Exception {
        return providerResponse(
                context,
                context.objectMapper().writeValueAsString(summary));
    }

    private String providerResponse(
            TestContext context,
            String output) throws Exception {
        return context.objectMapper().writeValueAsString(Map.of(
                "candidates", List.of(Map.of(
                        "content", Map.of(
                                "parts", List.of(Map.of(
                                        "text", output)))))));
    }

    private void expectOutput(TestContext context, String output)
            throws Exception {
        context.server()
                .expect(requestTo(containsString(
                        "/models/gemini-test:generateContent")))
                .andRespond(withSuccess(
                        providerResponse(context, output),
                        MediaType.APPLICATION_JSON));
    }

    private void assertErrorCode(
            ThrowingOperation operation,
            ErrorCode expected) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception).errorCode())
                .isEqualTo(expected);
    }

    private record TestContext(
            GeminiVideoSummaryService service,
            ObjectMapper objectMapper,
            MockRestServiceServer server) {
    }

    @FunctionalInterface
    private interface ThrowingOperation {

        void run() throws Exception;
    }
}
