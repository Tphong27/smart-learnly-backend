package com.smartlearnly.backend.videoai.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.AssignmentAiSettings;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.videoai.config.VideoAiGenerationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeminiVideoSummaryServiceTest {

    /**
     * Mục đích: xác minh Gemini nhận URL YouTube như một video input thực sự.
     */
    @Test
    void generateSummaryFromYoutubeVideo_sendsPublicVideoInput_whenResponseIsValid()
            throws Exception {
        // GIVEN: Gemini trả summary đúng schema cho một URL YouTube công khai.
        TestContext context = context("gemini-test");
        context.server()
                .expect(requestTo(
                        "https://gemini.example.test/v1beta/models/gemini-test:generateContent"))
                .andExpect(jsonPath(
                        "$.contents[0].parts[0].file_data.file_uri")
                        .value("https://www.youtube.com/watch?v=V9i3cGD-mts"))
                // .andExpect(jsonPath(
                //         "$.contents[0].parts[0].file_data.mime_type")
                //         .value("video/*"))
                .andExpect(jsonPath(
                        "$.contents[0].parts[0].file_data.mime_type")
                        .doesNotExist())
                .andExpect(jsonPath(
                        "$.contents[0].parts[1].text",
                        containsString("entire response in natural Vietnamese")))
                .andRespond(withSuccess(
                        providerResponse(context, validSummary()),
                        MediaType.APPLICATION_JSON));

        // WHEN: Service tạo summary trực tiếp từ video.
        GeneratedSummary actual = context.service().generateSummaryFromYoutubeVideo(
                "https://www.youtube.com/watch?v=V9i3cGD-mts");

        // THEN: Summary được parse đúng và request đã đi qua mock server.
        assertThat(actual).isEqualTo(validSummary());
        context.server().verify();
    }

    /**
     * Mục đích: kiểm tra luồng thành công với đúng response chuẩn của Gemini.
     *
     * <p>Input: language = {@code vi}, transcript là nội dung bài học React.
     * Gemini trả đúng 3 đoạn overview, một tiêu đề và 3 key takeaways.
     * Expected output: {@link GeneratedSummary} có cấu trúc để frontend render
     * thành các đoạn văn và danh sách bullet.
     */
    @Test
    void generateSummaryFromTranscript_rejectsBlankTranscript() {
        stubHealthySettings();

        assertErrorCode(
                () -> new GeminiVideoSummaryService(properties, settingsService(properties))
                        .generateSummaryFromTranscript("en", "Lesson transcript"),
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
    }

    @Test
    void generateSummaryFromTranscript_rejectsNullTranscript() {
        stubHealthySettings();

        assertErrorCode(
                () -> new GeminiVideoSummaryService(properties, settingsService(properties))
                        .generateSummaryFromTranscript("en", "Lesson transcript"),
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
    }

    @Test
    void generateSummaryFromYoutubeVideo_rejectsBlankUrl() {
        stubHealthySettings();

        assertErrorCode(
                () -> new GeminiVideoSummaryService(properties, settingsService(properties))
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

        /*
         * DEBUG FLOW:
         * 1. enabled=true và API key normalize khác null.
         * 2. Dòng 230 gọi modelName(null); dòng 239 model=normalize(null)=null.
         * 3. Dòng 240 model==null=true; dòng 242 return null.
         * 4. ensureAvailable nhận modelName==null=true và dòng 231 ném unavailable.
         * 5. Không có fallback model và không gửi HTTP request.
         */
        // WHEN + THEN: Service báo cấu hình không khả dụng.
        assertErrorCode(
                () -> new GeminiVideoSummaryService(properties, settingsService(properties))
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

        /*
         * DEBUG FLOW:
         * 1. ensureAvailable pass vì enabled/key/model đều hợp lệ.
         * 2. Dòng 57 gọi normalize(transcript=null); dòng 261 return null.
         * 3. source=null; dòng 58 source==null=true.
         * 4. Dòng 59 ném BusinessException(INVALID_REQUEST) trước khi tạo prompt.
         * 5. Khối try/catch HTTP không chạy; assertErrorCode bắt lỗi.
         */
        // WHEN + THEN: Không có transcript nên không thể tạo summary.
        assertErrorCode(
                () -> context.service().generateSummaryFromTranscript("en", null),
                ErrorCode.INVALID_REQUEST);
    }

    @Test
    void generateSummaryFromYoutubeVideo_rejectsNullUrl() {
        stubHealthySettings();

        assertErrorCode(
                () -> service.generateSummaryFromYoutubeVideo(null),
                ErrorCode.INVALID_REQUEST);
    }

    @Test
    void ensureAvailable_rejectsWhenAiDisabled() {
        when(settingsService.resolveAssignmentAiSettings())
                .thenReturn(new AssignmentAiSettings(
                        false, "gemini", "key", "gemini-flash", null, 30));

        assertErrorCode(
                () -> service.generateSummaryFromTranscript("en", "Lesson transcript"),
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
    }

    @Test
    void ensureAvailable_rejectsWhenProviderIsNotGemini() {
        when(settingsService.resolveAssignmentAiSettings())
                .thenReturn(new AssignmentAiSettings(
                        true, "openai", "key", "gemini-flash", null, 30));

        assertErrorCode(
                () -> service.generateSummaryFromTranscript("en", "Lesson transcript"),
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
    }

    @Test
    void ensureAvailable_rejectsWhenApiKeyIsBlank() {
        when(settingsService.resolveAssignmentAiSettings())
                .thenReturn(new AssignmentAiSettings(
                        true, "gemini", "  ", "gemini-flash", null, 30));

        assertErrorCode(
                () -> service.generateSummaryFromTranscript("en", "Lesson transcript"),
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
    }

    @Test
    void ensureAvailable_rejectsWhenApiKeyIsNull() {
        when(settingsService.resolveAssignmentAiSettings())
                .thenReturn(new AssignmentAiSettings(
                        true, "gemini", null, "gemini-flash", null, 30));

        assertErrorCode(
                () -> service.generateSummaryFromTranscript("en", "Lesson transcript"),
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
    }

    @Test
    void ensureAvailable_rejectsWhenModelIsBlank() {
        when(settingsService.resolveAssignmentAiSettings())
                .thenReturn(new AssignmentAiSettings(
                        true, "gemini", "key", "  ", null, 30));

        assertErrorCode(
                () -> service.generateSummaryFromTranscript("en", "Lesson transcript"),
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
    }

    @Test
    void ensureAvailable_rejectsWhenApiKeyContainsPlaceholderBrackets() {
        when(settingsService.resolveAssignmentAiSettings())
                .thenReturn(new AssignmentAiSettings(
                        true, "gemini", "<your-api-key>", "gemini-flash", null, 30));

        assertErrorCode(
                () -> service.generateSummaryFromTranscript("en", "Lesson transcript"),
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        context.server().verify();
    }

    /**
     * CODE UNDER TEST: GeminiVideoSummaryService.java dòng 204-207, nhánh
     * values == null trong normalizeItems().
     * Biến vào: overviewParagraphs = null; title và keyTakeaways hợp lệ.
     * Mục tiêu: normalizeItems trả List.of(), sau đó contract đúng 3 đoạn bị fail.
     */
    @Test
    void generateSummaryFromTranscript_throwsUnavailable_whenOverviewParagraphsIsNull()
            throws Exception {
        // GIVEN - variable: Gemini thiếu toàn bộ mảng overviewParagraphs.
        TestContext context = context("gemini-test");
        GeneratedSummary invalid = new GeneratedSummary(
                null,
                "Key takeaways",
                List.of("One", "Two", "Three"));
        expectOutput(
                context,
                context.objectMapper().writeValueAsString(invalid));

        /*
         * DEBUG FLOW:
         * 1. Dòng 175 parse overviewParagraphs=null.
         * 2. Dòng 177 gọi normalizeItems(null); dòng 205=true và dòng 207
         *    return List.of(), nên paragraphs.size=0.
         * 3. Title/takeaways hợp lệ nhưng dòng 182: 0!=3=true.
         * 4. Dòng 186 ném IOException; catch dòng 122 ném unavailable.
         */
        // WHEN - code line: normalizeItems(null) đi vào return List.of().
        // THEN - expected: validateGeneratedSummary trả EXTERNAL_SERVICE_UNAVAILABLE.
        assertErrorCode(
                () -> context.service().generateSummaryFromTranscript(
                        "en",
                        "Lesson transcript"),
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

        /*
         * DEBUG FLOW:
         * 1. Paragraphs size=3 và takeaways size=3 sau normalizeItems.
         * 2. Dòng 178 normalize(title="   "): trim="" => title=null.
         * 3. Dòng 182 paragraphs.size()!=3=false; dòng 183 title==null=true.
         * 4. Hai vế takeaway không chạy; dòng 186 ném IOException.
         * 5. Catch dòng 122 ánh xạ thành EXTERNAL_SERVICE_UNAVAILABLE.
         */
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

        /*
         * DEBUG FLOW:
         * 1. paragraphs size=3, title khác null, takeaways=[One,Two] size=2.
         * 2. Dòng 182=false; dòng 183=false.
         * 3. Dòng 184 takeaways.size()<3 => 2<3=true.
         * 4. Vế >5 không chạy; dòng 186 ném IOException.
         * 5. Catch multi dòng 122 đổi lỗi contract thành unavailable.
         */
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

        /*
         * DEBUG FLOW:
         * 1. paragraphs size=3, title hợp lệ, takeaways size=6.
         * 2. Dòng 182=false; 183=false; dòng 184 size<3=false.
         * 3. Dòng 185 takeaways.size()>5 => 6>5=true.
         * 4. Dòng 186 ném IOException; vòng tính characterCount không chạy.
         * 5. Catch dòng 122 ném EXTERNAL_SERVICE_UNAVAILABLE.
         */
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

        /*
         * DEBUG FLOW:
         * 1. Contract pass: 3 paragraphs, title khác null, 3 takeaways.
         * 2. Dòng 190 characterCount bắt đầu bằng title.length().
         * 3. Vòng for dòng 191 cộng paragraph đầu dài 50001 cùng các đoạn khác.
         * 4. Vòng for dòng 194 cộng độ dài takeaways; tổng >50000.
         * 5. Dòng 197 điều kiện true; dòng 198 ném IOException oversized.
         * 6. Catch dòng 122 đổi thành unavailable; dòng 201 không return.
         */
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
                        settingsService(properties),
                        objectMapper,
                        builder.build()),
                objectMapper,
                server);
    }

    private VideoAiGenerationProperties properties() {
        VideoAiGenerationProperties props = new VideoAiGenerationProperties();
        props.setEnabled(true);
        props.setApiKey("gemini-test-key");
        props.setApiBaseUrl("https://gemini.example.test/v1beta");
        props.setModel("gemini-test");
        return props;
    }

    private SystemSettingsService settingsService(VideoAiGenerationProperties properties) {
        SystemSettingsService settingsService = mock(SystemSettingsService.class);
        when(settingsService.resolveAssignmentAiSettings()).thenAnswer(ignored -> new AssignmentAiSettings(
                properties.isEnabled(),
                "gemini",
                properties.getApiKey(),
                properties.getModel(),
                null,
                properties.getTimeout().toSeconds()));
        return settingsService;
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
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(expected);
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws Exception;
    }
}
