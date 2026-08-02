package com.smartlearnly.backend.videoai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.videoai.config.VideoAiProperties;
import com.smartlearnly.backend.videoai.service.YoutubeVideoMetadataService.YoutubeVideoMetadata;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Unit test riêng cho {@link YoutubeVideoMetadataService}.
 *
 * <p>
 * Mỗi test gọi trực tiếp {@code fetchYoutubeVideoMetadata()} nên các trường
 * hợp metadata không còn bị gộp vào test của generateVideoSummary().
 */
class YoutubeVideoMetadataServiceTest {

        private static final String VIDEO_ID = "V9i3cGD-mts";

        private VideoAiProperties properties;
        private MockRestServiceServer youtubeServer;
        private YoutubeVideoMetadataService service;

        @BeforeEach
        void setUp() {
                // GIVEN: Cấu hình YouTube hợp lệ dùng chung cho các test.
                properties = new VideoAiProperties();
                properties.setEnabled(true);
                properties.setYoutubeApiKey("youtube-test-key");
                properties.setYoutubeApiBaseUrl("https://youtube.example.test");

                // GIVEN: RestClient được gắn mock server để không gọi YouTube thật.
                RestClient.Builder builder = RestClient.builder()
                                .baseUrl(properties.getYoutubeApiBaseUrl());
                youtubeServer = MockRestServiceServer.bindTo(builder).build();
                service = new YoutubeVideoMetadataService(
                                properties,
                                builder.build());
        }

        @Test
        void fetchYoutubeVideoMetadata_returnsMetadata_whenYoutubeResponseIsValid() {
                // GIVEN: Video dài 17 phút 1 giây, có caption và cho phép embed.
                expectMetadata("PT17M1S", "true", true);

                /*
                 * DEBUG FLOW:
                 * 1. Dòng 48 validate config: enabled=true, apiKey="youtube-test-key";
                 * điều kiện dòng 116-118=false.
                 * 2. Dòng 50-58 mock HTTP trả items[0] với duration="PT17M1S",
                 * caption="true", embeddable=true.
                 * 3. Dòng 63 items là array và không rỗng => if=false.
                 * 4. Dòng 73 !embeddable=false; dòng 81 !captionsAvailable=false.
                 * 5. Dòng 87-91 Duration.parse(...).toSeconds() => 1021.
                 * 6. maximumSeconds=120*60=7200; dòng 94: 1021<=0=false,
                 * 1021>7200=false; dòng 99 return metadata(1021).
                 */
                // WHEN: Service lấy metadata theo video ID.
                YoutubeVideoMetadata actualMetadata = service.fetchYoutubeVideoMetadata(VIDEO_ID);

                // THEN: Thời lượng phải được chuyển thành 1021 giây.
                assertThat(actualMetadata.durationSeconds()).isEqualTo(1_021);
                youtubeServer.verify();
        }

        @Test
        void fetchYoutubeVideoMetadata_throwsBusinessRuleViolation_whenVideoIsNotEmbeddable() {
                // GIVEN: Video tồn tại nhưng embeddable bằng false.
                expectMetadata("PT17M1S", "true", false);

                /*
                 * DEBUG FLOW:
                 * 1. Config hợp lệ; HTTP mock trả items array có một video.
                 * 2. Dòng 70-72 đọc embeddable=false.
                 * 3. Dòng 73: !embeddable=true.
                 * 4. Dòng 74 ném BusinessException(BUSINESS_RULE_VIOLATION,
                 * "This YouTube video cannot be embedded").
                 * 5. Hai catch dòng 100/106 không bắt BusinessException; caption và
                 * duration không được đọc; catchThrowable nhận exception.
                 */
                // WHEN: Service kiểm tra quyền nhúng.
                Throwable actualException = catchThrowable(
                                () -> service.fetchYoutubeVideoMetadata(VIDEO_ID));

                // THEN: Video không cho nhúng phải bị từ chối.
                assertErrorCode(
                                actualException,
                                ErrorCode.BUSINESS_RULE_VIOLATION);
                assertThat(actualException)
                                .hasMessage("This YouTube video cannot be embedded");
                youtubeServer.verify();
        }

        @Test
        void fetchYoutubeVideoMetadata_throwsBusinessRuleViolation_whenVideoHasNoCaptions() {
                // GIVEN: Video cho phép embed nhưng caption bằng false.
                expectMetadata("PT17M1S", "false", true);

                /*
                 * DEBUG FLOW:
                 * 1. Dòng 73: embeddable=true nên !embeddable=false, tiếp tục.
                 * 2. Dòng 79-80 đọc caption="false";
                 * "true".equalsIgnoreCase("false")=false.
                 * 3. Dòng 81: !captionsAvailable=true.
                 * 4. Dòng 82 ném BUSINESS_RULE_VIOLATION với message thiếu captions.
                 * 5. Duration dòng 87 không chạy; catchThrowable bắt BusinessException.
                 */
                // WHEN: Service kiểm tra caption.
                Throwable actualException = catchThrowable(
                                () -> service.fetchYoutubeVideoMetadata(VIDEO_ID));

                // THEN: Video thiếu caption phải bị từ chối.
                assertErrorCode(
                                actualException,
                                ErrorCode.BUSINESS_RULE_VIOLATION);
                assertThat(actualException)
                                .hasMessage("This YouTube video does not have captions");
                youtubeServer.verify();
        }

        @Test
        void fetchYoutubeVideoMetadata_throwsBusinessRuleViolation_whenDurationIsZero() {
                // GIVEN: YouTube trả video có thời lượng 0 giây.
                expectMetadata("PT0S", "true", true);

                /*
                 * DEBUG FLOW:
                 * 1. items hợp lệ, embeddable=true, captionsAvailable=true.
                 * 2. Dòng 87-91: Duration.parse("PT0S").toSeconds()=0.
                 * 3. maximumSeconds=7200.
                 * 4. Dòng 94: durationSeconds<=0 => 0<=0=true; vế > maximum không chạy.
                 * 5. Dòng 95 ném BUSINESS_RULE_VIOLATION; không return metadata.
                 * 6. catchThrowable nhận exception trực tiếp từ khối try.
                 */
                // WHEN: Service kiểm tra thời lượng.
                Throwable actualException = catchThrowable(
                                () -> service.fetchYoutubeVideoMetadata(VIDEO_ID));

                // THEN: Video không có thời lượng hợp lệ phải bị từ chối.
                assertErrorCode(
                                actualException,
                                ErrorCode.BUSINESS_RULE_VIOLATION);
                youtubeServer.verify();
        }

        @Test
        void fetchYoutubeVideoMetadata_throwsBusinessRuleViolation_whenDurationExceedsLimit() {
                // GIVEN: Video dài 121 phút, vượt giới hạn 120 phút.
                expectMetadata("PT2H1M", "true", true);

                /*
                 * DEBUG FLOW:
                 * 1. Duration.parse("PT2H1M").toSeconds()=7260 (121 phút).
                 * 2. maxVideoDurationMinutes mặc định=120 => maximumSeconds=7200.
                 * 3. Dòng 94: 7260<=0=false; 7260>7200=true.
                 * 4. Dòng 95 ném BUSINESS_RULE_VIOLATION với message tối đa 120 phút.
                 * 5. BusinessException không bị multi-catch bắt; catchThrowable nhận nó.
                 */
                // WHEN: Service kiểm tra thời lượng.
                Throwable actualException = catchThrowable(
                                () -> service.fetchYoutubeVideoMetadata(VIDEO_ID));

                // THEN: Video quá dài phải bị từ chối.
                assertErrorCode(
                                actualException,
                                ErrorCode.BUSINESS_RULE_VIOLATION);
                assertThat(actualException)
                                .hasMessage("YouTube video must be 120 minutes or shorter");
                youtubeServer.verify();
        }

        @Test
        void fetchYoutubeVideoMetadata_throwsResourceNotFound_whenItemsIsEmpty() {
                // GIVEN: YouTube trả mảng items rỗng.
                expectRawMetadata("{\"items\":[]}");

                /*
                 * DEBUG FLOW:
                 * 1. Dòng 59 parse JSON => response có items=[].
                 * 2. Dòng 62: items là ArrayNode, không null.
                 * 3. Dòng 63: items==null=false; !items.isArray=false;
                 * items.isEmpty=true => toàn bộ điều kiện true.
                 * 4. Dòng 64 ném BusinessException(RESOURCE_NOT_FOUND,
                 * "YouTube video was not found").
                 * 5. Không đọc items.get(0); catchThrowable bắt exception.
                 */
                // WHEN: Service tìm video theo ID.
                Throwable actualException = catchThrowable(
                                () -> service.fetchYoutubeVideoMetadata(VIDEO_ID));

                // THEN: Không có item tương ứng phải trả RESOURCE_NOT_FOUND.
                assertErrorCode(actualException, ErrorCode.RESOURCE_NOT_FOUND);
                assertThat(actualException).hasMessage("YouTube video was not found");
                youtubeServer.verify();
        }

        @Test
        void fetchYoutubeVideoMetadata_throwsResourceNotFound_whenItemsIsNotArray() {
                // GIVEN: YouTube trả items là object thay vì array.
                expectRawMetadata("{\"items\":{}}");

                /*
                 * DEBUG FLOW:
                 * 1. Dòng 62 lấy items={} kiểu ObjectNode.
                 * 2. Dòng 63: items==null=false; !items.isArray()=true.
                 * 3. Do || short-circuit, items.isEmpty() không cần quyết định nhánh.
                 * 4. Dòng 64 ném BusinessException(RESOURCE_NOT_FOUND).
                 * 5. BusinessException truyền ra khỏi try; catchThrowable nhận nó.
                 */
                // WHEN: Service đọc cấu trúc response.
                Throwable actualException = catchThrowable(
                                () -> service.fetchYoutubeVideoMetadata(VIDEO_ID));

                // THEN: Response không chứa danh sách video hợp lệ.
                assertErrorCode(actualException, ErrorCode.RESOURCE_NOT_FOUND);
                youtubeServer.verify();
        }

        @Test
        void fetchYoutubeVideoMetadata_throwsResourceNotFound_whenResponseJsonIsNull() {
                // GIVEN: YouTube trả JSON literal null.
                expectRawMetadata("null");

                /*
                 * DEBUG FLOW:
                 * 1. Dòng 59 readTree("null") trả NullNode (object tồn tại, không phải Java
                 * null).
                 * 2. Dòng 62 response.path("items") trả MissingNode, không trả null.
                 * 3. Dòng 63: items==null=false; !items.isArray()=true.
                 * 4. Dòng 64 ném RESOURCE_NOT_FOUND; không phát sinh NullPointerException.
                 * 5. catchThrowable nhận BusinessException để assertion kiểm tra.
                 */
                // WHEN: Service đọc root JSON.
                Throwable actualException = catchThrowable(
                                () -> service.fetchYoutubeVideoMetadata(VIDEO_ID));

                // THEN: Root null không chứa video.
                assertErrorCode(actualException, ErrorCode.RESOURCE_NOT_FOUND);
                youtubeServer.verify();
        }

        @Test
        void fetchYoutubeVideoMetadata_throwsUnavailable_whenResponseJsonIsMalformed() {
                // GIVEN: YouTube trả nội dung không phải JSON hợp lệ.
                expectRawMetadata("{not-json");

                /*
                 * DEBUG FLOW:
                 * 1. HTTP mock trả body="{not-json" thành công.
                 * 2. Dòng 59 youtubeJson.readTree(responseBody) ném JsonParseException,
                 * là subclass của IOException.
                 * 3. Catch HTTP dòng 100 không chạy; multi-catch dòng 106 bắt IOException.
                 * 4. Dòng 111 ném BusinessException(EXTERNAL_SERVICE_UNAVAILABLE).
                 * 5. catchThrowable nhận lỗi đã được service chuẩn hóa.
                 */
                // WHEN: Service parse response.
                Throwable actualException = catchThrowable(
                                () -> service.fetchYoutubeVideoMetadata(VIDEO_ID));

                // THEN: Lỗi JSON phải được map thành unavailable.
                assertErrorCode(
                                actualException,
                                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
                youtubeServer.verify();
        }

        @Test
        void fetchYoutubeVideoMetadata_throwsResourceNotFound_whenResponseBodyIsEmpty() {
                // GIVEN: YouTube trả HTTP thành công nhưng không có body.
                youtubeServer.expect(requestTo(containsString("/videos?")))
                                .andRespond(withNoContent());

                /*
                 * DEBUG FLOW:
                 * 1. HTTP 204 làm responseBody tại dòng 50 bằng null.
                 * 2. Dòng 60 dùng toán tử ba ngôi: responseBody==null=true => parse "{}".
                 * 3. response.path("items") trả MissingNode.
                 * 4. Dòng 63: !items.isArray()=true; dòng 64 ném RESOURCE_NOT_FOUND.
                 * 5. Đây là lỗi nghiệp vụ, không đi vào hai catch lỗi nhà cung cấp.
                 */
                // WHEN: Service đọc response.
                Throwable actualException = catchThrowable(
                                () -> service.fetchYoutubeVideoMetadata(VIDEO_ID));

                // THEN: Không có video item phải trả RESOURCE_NOT_FOUND.
                assertErrorCode(actualException, ErrorCode.RESOURCE_NOT_FOUND);
                youtubeServer.verify();
        }

        @Test
        void fetchYoutubeVideoMetadata_throwsUnavailable_whenYoutubeReturnsRateLimit() {
                // GIVEN: YouTube API trả HTTP 429 do hết quota.
                youtubeServer.expect(requestTo(containsString("/videos?")))
                                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .body("{\"error\":{\"message\":\"quota\"}}"));

                /*
                 * DEBUG FLOW:
                 * 1. Dòng 50-58 gửi GET /videos với part, id=VIDEO_ID và API key.
                 * 2. Mock server trả HTTP 429; retrieve().body(...) ném
                 * RestClientResponseException trước khi responseBody được gán.
                 * 3. Catch riêng dòng 100 bắt HTTP exception; status=429 được log.
                 * 4. Dòng 105 ném BusinessException(EXTERNAL_SERVICE_UNAVAILABLE).
                 * 5. Catch dòng 106 không chạy vì catch HTTP đứng trước.
                 */
                // WHEN: Service gọi YouTube API.
                Throwable actualException = catchThrowable(
                                () -> service.fetchYoutubeVideoMetadata(VIDEO_ID));

                // THEN: Lỗi nhà cung cấp được ẩn sau error code thống nhất.
                assertErrorCode(
                                actualException,
                                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
                youtubeServer.verify();
        }

        @Test
        void fetchYoutubeVideoMetadata_throwsUnavailable_whenFeatureIsDisabled() {
                // GIVEN: Administrator tắt tính năng video AI.
                properties.setEnabled(false);

                /*
                 * DEBUG FLOW:
                 * 1. Dòng 48 gọi validateYoutubeMetadataConfiguration().
                 * 2. Dòng 116: !properties.isEnabled() = !false = true.
                 * 3. Toán tử || short-circuit nên apiKey null/blank không cần kiểm tra.
                 * 4. Dòng 119 ném BusinessException(EXTERNAL_SERVICE_UNAVAILABLE,
                 * "YouTube summary is not configured") trước khối try dòng 49.
                 * 5. Không có request tới youtubeServer; catchThrowable bắt exception.
                 */
                // WHEN: Service chuẩn bị gọi YouTube.
                Throwable actualException = catchThrowable(
                                () -> service.fetchYoutubeVideoMetadata(VIDEO_ID));

                // THEN: Service phải dừng trước request HTTP.
                assertErrorCode(
                                actualException,
                                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
                assertThat(actualException)
                                .hasMessage("YouTube summary is not configured");
        }

        @Test
        void fetchYoutubeVideoMetadata_throwsUnavailable_whenApiKeyIsNull() {
                // GIVEN: YouTube API key chưa được cấu hình.
                properties.setYoutubeApiKey(null);

                /*
                 * DEBUG FLOW:
                 * 1. validate config: enabled=true nên vế dòng 116=false.
                 * 2. Dòng 117: youtubeApiKey==null=true.
                 * 3. Vế isBlank ở dòng 118 không chạy do || short-circuit.
                 * 4. Dòng 119 ném EXTERNAL_SERVICE_UNAVAILABLE trước HTTP try.
                 * 5. catchThrowable nhận exception; youtubeServer không được gọi.
                 */
                // WHEN: Service kiểm tra cấu hình.
                Throwable actualException = catchThrowable(
                                () -> service.fetchYoutubeVideoMetadata(VIDEO_ID));

                // THEN: API key null phải bị từ chối.
                assertErrorCode(
                                actualException,
                                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        }

        @Test
        void fetchYoutubeVideoMetadata_throwsUnavailable_whenApiKeyIsBlank() {
                // GIVEN: YouTube API key chỉ chứa khoảng trắng.
                properties.setYoutubeApiKey("   ");

                /*
                 * DEBUG FLOW:
                 * 1. enabled=true => dòng 116 false.
                 * 2. apiKey="   " khác null => dòng 117 false.
                 * 3. Dòng 118 apiKey.isBlank()=true.
                 * 4. Dòng 119 ném EXTERNAL_SERVICE_UNAVAILABLE trước khi tạo URI/request.
                 * 5. catchThrowable nhận BusinessException; assertion kiểm tra errorCode.
                 */
                // WHEN: Service kiểm tra cấu hình.
                Throwable actualException = catchThrowable(
                                () -> service.fetchYoutubeVideoMetadata(VIDEO_ID));

                // THEN: API key blank phải bị từ chối.
                assertErrorCode(
                                actualException,
                                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        }

        @Test
        void constructor_createsMetadataService_whenTimeoutIsZero() {
                // GIVEN: Timeout bằng 0 phải dùng giá trị mặc định.
                VideoAiProperties configuration = new VideoAiProperties();
                configuration.setYoutubeApiTimeout(Duration.ZERO);

                /*
                 * DEBUG FLOW:
                 * 1. Dòng 32 constructor public gọi createYoutubeClient(configuration).
                 * 2. Dòng 134 timeout=Duration.ZERO.
                 * 3. Dòng 135: timeout==null=false; isZero=true; vế isNegative không chạy.
                 * 4. Dòng 136 timeout được thay bằng Duration.ofSeconds(20).
                 * 5. Dòng 138-143 tạo RestClient; dòng 33 chuyển sang constructor nội bộ
                 * và gán properties/youtubeClient; không có exception.
                 */
                // WHEN: Metadata service được khởi tạo.
                YoutubeVideoMetadataService actualService = new YoutubeVideoMetadataService(configuration);

                // THEN: Constructor không được ném exception.
                assertThat(actualService).isNotNull();
        }

        @Test
        void constructor_createsMetadataService_whenTimeoutIsNegative() {
                // GIVEN: Timeout âm không thể dùng trực tiếp.
                VideoAiProperties configuration = new VideoAiProperties();
                configuration.setYoutubeApiTimeout(Duration.ofSeconds(-1));

                /*
                 * DEBUG FLOW:
                 * 1. createYoutubeClient đọc timeout=-1 giây.
                 * 2. Dòng 135: timeout null=false, zero=false, negative=true.
                 * 3. Dòng 136 fallback timeout=20 giây.
                 * 4. Connect/read timeout nhận giá trị hợp lệ; RestClient được tạo.
                 * 5. Constructor dòng 36-40 gán dependency và trả service khác null.
                 */
                // WHEN: Metadata service được khởi tạo.
                YoutubeVideoMetadataService actualService = new YoutubeVideoMetadataService(configuration);

                // THEN: Constructor phải fallback về timeout mặc định.
                assertThat(actualService).isNotNull();
        }

        @Test
        void constructor_createsMetadataService_whenTimeoutIsNull() {
                // GIVEN: Cấu hình không cung cấp timeout.
                VideoAiProperties configuration = new VideoAiProperties();
                configuration.setYoutubeApiTimeout(null);

                /*
                 * DEBUG FLOW:
                 * 1. Dòng 134 timeout=null.
                 * 2. Dòng 135 timeout==null=true; hai vế isZero/isNegative không được gọi,
                 * nên không xảy ra NullPointerException.
                 * 3. Dòng 136 gán timeout mặc định 20 giây.
                 * 4. Dòng 138-143 tạo RestClient và constructor hoàn tất.
                 */
                // WHEN: Metadata service được khởi tạo.
                YoutubeVideoMetadataService actualService = new YoutubeVideoMetadataService(configuration);

                // THEN: Constructor phải dùng timeout mặc định.
                assertThat(actualService).isNotNull();
        }

        @Test
        void constructor_createsMetadataService_whenTimeoutIsPositive() {
                // GIVEN: Timeout 5 giây là hợp lệ.
                VideoAiProperties configuration = new VideoAiProperties();
                configuration.setYoutubeApiTimeout(Duration.ofSeconds(5));

                /*
                 * DEBUG FLOW:
                 * 1. Dòng 134 timeout=5 giây.
                 * 2. Dòng 135: null=false, zero=false, negative=false; không fallback.
                 * 3. Dòng 138-139 dùng chính timeout=5 giây cho connect/read.
                 * 4. Dòng 140-143 tạo RestClient; constructor trả service khác null.
                 */
                // WHEN: Metadata service được khởi tạo.
                YoutubeVideoMetadataService actualService = new YoutubeVideoMetadataService(configuration);

                // THEN: Constructor phải chấp nhận cấu hình.
                assertThat(actualService).isNotNull();
        }

        private void expectMetadata(
                        String duration,
                        String caption,
                        boolean embeddable) {
                youtubeServer.expect(requestTo(containsString("/videos?")))
                                .andRespond(withSuccess(
                                                """
                                                                {
                                                                  "items": [{
                                                                    "contentDetails": {
                                                                      "duration": "%s",
                                                                      "caption": "%s"
                                                                    },
                                                                    "status": {
                                                                      "embeddable": %s
                                                                    }
                                                                  }]
                                                                }
                                                                """.formatted(
                                                                duration,
                                                                caption,
                                                                embeddable),
                                                MediaType.APPLICATION_JSON));
        }

        private void expectRawMetadata(String body) {
                youtubeServer.expect(requestTo(containsString("/videos?")))
                                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        }

        private void assertErrorCode(
                        Throwable actualException,
                        ErrorCode expectedErrorCode) {
                assertThat(actualException)
                                .isInstanceOf(BusinessException.class)
                                .extracting(exception -> ((BusinessException) exception).errorCode())
                                .isEqualTo(expectedErrorCode);
        }
}
