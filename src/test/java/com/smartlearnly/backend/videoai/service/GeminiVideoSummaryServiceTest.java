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
import java.util.Arrays;
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
                .andExpect(jsonPath(
                        "$.contents[0].parts[0].file_data.mime_type")
                        .value("video/*"))
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
                        containsString("transcript language hint is: vi")))
                .andExpect(jsonPath(
                        "$.contents[0].parts[0].text",
                        containsString("entire response in natural Vietnamese")))
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

        /*
         * DEBUG FLOW:
         * 1. Dòng 55 ensureAvailable pass: enabled=true, key hợp lệ,
         *    model "models/gemini-test" normalize thành "gemini-test" dòng 245-247.
         * 2. Dòng 57 source="Bài học giải thích React state"; dòng 58=false.
         * 3. Dòng 64-91 tạo prompt language="vi"; requestBody dòng 133-166 có
         *    schema 3 paragraphs, 3-5 takeaways và store=false.
         * 4. HTTP mock xác minh URL/header/prompt/schema rồi trả providerResponse.
         * 5. Dòng 106-112 lấy output tại candidates[0].content.parts[0].text.
         * 6. Dòng 177-179 normalize: trim paragraph/title và bỏ -, *, •.
         * 7. Dòng 182-198 contract/size hợp lệ; dòng 201 return GeneratedSummary.
         */
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
     * CODE UNDER TEST: GeminiVideoSummaryService.java dòng 204-224,
     * hai filter trong normalizeItems().
     * Biến vào: overview có phần tử null/blank; takeaways có phần tử chỉ là "-".
     * Mục tiêu: đi qua cả nhánh giữ và nhánh loại bỏ của từng filter.
     */
    @Test
    void generateSummaryFromTranscript_filtersNullBlankAndBulletOnlyItems()
            throws Exception {
        // GIVEN - variables: sau lọc vẫn còn đúng 3 paragraphs và 3 takeaways.
        TestContext context = context("gemini-test");
        GeneratedSummary providerSummary = new GeneratedSummary(
                Arrays.asList(" One ", null, "   ", "Two", "Three"),
                " Key takeaways ",
                List.of("-", "* First", "• Second", "- Third"));

        // MOCK - Gemini trả đúng envelope nhưng list có các item cần loại bỏ.
        expectOutput(
                context,
                context.objectMapper().writeValueAsString(providerSummary));

        /*
         * DEBUG FLOW:
         * 1. Dòng 175 parse output thành GeneratedSummary có overview 5 phần tử.
         * 2. normalizeItems dòng 209 duyệt từng item:
         *    " One "=>"One"; null=>null; "   "=>null; "Two"/"Three" giữ lại.
         * 3. Filter dòng 214 loại null và blank => paragraphs=[One,Two,Three].
         * 4. Takeaway "-" qua replaceFirst thành "", normalize thành null và bị
         *    filter dòng 221 loại; ba item còn lại bỏ bullet thành First/Second/Third.
         * 5. Title trim thành "Key takeaways"; contract 3+3 pass; dòng 201 return.
         */
        // WHEN - code line: parseGeneratedSummary gọi normalizeItems/normalizeBullets.
        GeneratedSummary actual = context.service()
                .generateSummaryFromTranscript("en", "Lesson transcript");

        // THEN - expected: null, blank và bullet-only bị loại; nội dung còn lại được trim.
        assertThat(actual.overviewParagraphs())
                .containsExactly("One", "Two", "Three");
        assertThat(actual.keyTakeawaysTitle())
                .isEqualTo("Key takeaways");
        assertThat(actual.keyTakeaways())
                .containsExactly("First", "Second", "Third");
        context.server().verify();
    }

    /**
     * Mục đích: kiểm tra language không có giá trị.
     *
     * <p>Input: language = null, transcript hợp lệ.
     * Expected output: prompt vẫn ghi nhận ngôn ngữ được phát hiện làm gợi ý,
     * nhưng bắt buộc toàn bộ output dùng tiếng Việt.
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
                                "transcript language hint is: "
                                        + "the detected language")))
                .andExpect(jsonPath(
                        "$.contents[0].parts[0].text",
                        containsString("entire response in natural Vietnamese")))
                .andRespond(withSuccess(
                        providerResponse(context, validSummary()),
                        MediaType.APPLICATION_JSON));

        /*
         * DEBUG FLOW:
         * 1. ensureAvailable pass; transcript normalize thành "Lesson transcript".
         * 2. Dòng 253 normalizeLanguage(value=null); dòng 261 normalize trả null.
         * 3. Dòng 257: normalized==null=true => "the detected language".
         * 4. Prompt chứa đúng câu mock đang kiểm tra; HTTP trả validSummary.
         * 5. parseSummary contract pass và return object bằng validSummary().
         */
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

        /*
         * DEBUG FLOW:
         * 1. Dòng 55 gọi ensureAvailable().
         * 2. Dòng 228 !properties.isEnabled()=!false=true.
         * 3. Toán tử || short-circuit nên API key/model không cần kiểm tra.
         * 4. Dòng 231 ném BusinessException(EXTERNAL_SERVICE_UNAVAILABLE)
         *    trước normalize transcript và trước HTTP try dòng 94.
         * 5. assertErrorCode bắt exception từ lambda.
         */
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

        /*
         * DEBUG FLOW:
         * 1. ensureAvailable: enabled=true nên vế dòng 228 đầu=false.
         * 2. normalize(apiKey=null) dòng 261 return null.
         * 3. Dòng 229 so sánh null==null => true; model không cần xét.
         * 4. Dòng 231 ném unavailable; không tạo RestClient request.
         */
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

        /*
         * DEBUG FLOW:
         * 1. enabled=true; normalize(apiKey="   ") gọi trim => "".
         * 2. Dòng 270 normalized.isEmpty()=true => normalize return null.
         * 3. Dòng 229 điều kiện API key=true; dòng 231 ném unavailable.
         * 4. Transcript/model/HTTP không được xử lý.
         */
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

    /**
     * Mục đích: transcript chỉ có khoảng trắng không phải nội dung hợp lệ.
     *
     * <p>Input: transcript = " \n ". Expected output: INVALID_REQUEST.
     */
    @Test
    void generateSummaryFromTranscript_throwsInvalidRequest_whenTranscriptIsBlank() {
        // GIVEN: Service đã có cấu hình Gemini hợp lệ.
        TestContext context = context("gemini-test");

        /*
         * DEBUG FLOW:
         * 1. ensureAvailable pass.
         * 2. Dòng 57 normalize(" \n "): trim => ""; dòng 270 return null.
         * 3. Dòng 58 source==null=true; dòng 59 ném INVALID_REQUEST.
         * 4. Không tạo prompt/request; assertErrorCode kiểm tra exception.
         */
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

        /*
         * DEBUG FLOW:
         * 1. modelName("gemini-only") trả "gemini-only"; source hợp lệ.
         * 2. Dòng 95-101 POST đúng model; mock trả HTTP 400.
         * 3. retrieve().body() ném RestClientResponseException.
         * 4. Catch chuyên biệt dòng 115 bắt HTTP error; dòng 121 ném
         *    BusinessException(EXTERNAL_SERVICE_UNAVAILABLE).
         * 5. Catch multi dòng 122 không chạy và service không thử model thứ hai.
         */
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

        /*
         * DEBUG FLOW:
         * 1. HTTP 204 làm responseBody=null.
         * 2. Dòng 104 toán tử ba ngôi chọn "{}"; readTree tạo ObjectNode rỗng.
         * 3. Dòng 106-112 dùng path() qua candidates/content/parts/text;
         *    MissingNode.asText(null) => output=null.
         * 4. Dòng 171 normalize(output)==null=true; dòng 172 ném IOException.
         * 5. Catch dòng 122 bắt IOException và dòng 128 ném unavailable.
         */
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

        /*
         * DEBUG FLOW:
         * 1. Response root chỉ có field text; không có candidates.
         * 2. Chuỗi path dòng 106-112 trả MissingNode rồi output=null.
         * 3. parseSummary dòng 171 thấy normalize(null)==null.
         * 4. Dòng 172 ném IOException("Gemini returned an empty summary").
         * 5. Multi-catch dòng 122 bắt IOException; dòng 128 ném unavailable.
         */
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

        /*
         * DEBUG FLOW:
         * 1. Provider envelope đúng nên output="{\"summary\":\"Old summary\"}".
         * 2. Dòng 171 output không blank; dòng 175 cố deserialize vào
         *    GeneratedSummary chỉ cho overviewParagraphs/title/keyTakeaways.
         * 3. Field legacy "summary" không được nhận => ObjectMapper ném
         *    UnrecognizedPropertyException (subclass IOException).
         * 4. Catch dòng 122 bắt lỗi; dòng 128 ném EXTERNAL_SERVICE_UNAVAILABLE.
         */
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

        /*
         * DEBUG FLOW:
         * 1. Envelope hợp lệ; output tại dòng 112="not-json".
         * 2. Dòng 171 normalize(output) khác null nên tiếp tục.
         * 3. Dòng 175 objectMapper.readValue("not-json", GeneratedSummary.class)
         *    ném JsonParseException, là IOException.
         * 4. Catch multi dòng 122 bắt lỗi và dòng 128 ném unavailable.
         */
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

        /*
         * DEBUG FLOW:
         * 1. Dòng 175 parse thành paragraphs=[Paragraph one, Paragraph two],
         *    title="Key takeaways", takeaways size=3.
         * 2. normalizeItems giữ lại 2 paragraphs vì chúng không blank.
         * 3. Dòng 182 paragraphs.size()!=3 => 2!=3=true.
         * 4. Các vế title/takeaways không cần xét do || short-circuit.
         * 5. Dòng 186 ném IOException; catch dòng 122 đổi thành unavailable.
         */
        // WHEN + THEN: Output thiếu paragraph phải bị từ chối.
        assertErrorCode(
                () -> context.service().generateSummaryFromTranscript("en", "Lesson transcript"),
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
