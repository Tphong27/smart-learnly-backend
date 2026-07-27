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
 * <p>Mỗi test gọi trực tiếp {@code fetchYoutubeVideoMetadata()} nên các trường
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

        // WHEN: Service lấy metadata theo video ID.
        YoutubeVideoMetadata actualMetadata =
                service.fetchYoutubeVideoMetadata(VIDEO_ID);

        // THEN: Thời lượng phải được chuyển thành 1021 giây.
        assertThat(actualMetadata.durationSeconds()).isEqualTo(1_021);
        youtubeServer.verify();
    }

    @Test
    void fetchYoutubeVideoMetadata_throwsBusinessRuleViolation_whenVideoIsNotEmbeddable() {
        // GIVEN: Video tồn tại nhưng embeddable bằng false.
        expectMetadata("PT17M1S", "true", false);

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
    void fetchYoutubeVideoMetadata_returnsMetadata_whenDurationEqualsLimit() {
        // GIVEN: Giới hạn và thời lượng video đều bằng đúng 1 phút.
        properties.setMaxVideoDurationMinutes(1);
        expectMetadata("PT1M", "TRUE", true);

        // WHEN: Service kiểm tra boundary.
        YoutubeVideoMetadata actualMetadata =
                service.fetchYoutubeVideoMetadata(VIDEO_ID);

        // THEN: Giá trị bằng giới hạn phải được chấp nhận.
        assertThat(actualMetadata.durationSeconds()).isEqualTo(60);
        youtubeServer.verify();
    }

    @Test
    void fetchYoutubeVideoMetadata_usesOneMinuteMinimum_whenConfiguredLimitIsZero() {
        // GIVEN: Cấu hình giới hạn bằng 0 và video dài 1 phút.
        properties.setMaxVideoDurationMinutes(0);
        expectMetadata("PT1M", "true", true);

        // WHEN: Service chuẩn hóa giới hạn tối thiểu.
        YoutubeVideoMetadata actualMetadata =
                service.fetchYoutubeVideoMetadata(VIDEO_ID);

        // THEN: Video 1 phút vẫn được chấp nhận.
        assertThat(actualMetadata.durationSeconds()).isEqualTo(60);
        youtubeServer.verify();
    }

    @Test
    void fetchYoutubeVideoMetadata_throwsUnavailable_whenDurationFormatIsInvalid() {
        // GIVEN: YouTube trả duration không đúng ISO-8601.
        expectMetadata("not-a-duration", "true", true);

        // WHEN: Service parse thời lượng.
        Throwable actualException = catchThrowable(
                () -> service.fetchYoutubeVideoMetadata(VIDEO_ID));

        // THEN: Response ngoài không đọc được phải được map thành unavailable.
        assertErrorCode(
                actualException,
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        youtubeServer.verify();
    }

    @Test
    void fetchYoutubeVideoMetadata_throwsResourceNotFound_whenItemsIsEmpty() {
        // GIVEN: YouTube trả mảng items rỗng.
        expectRawMetadata("{\"items\":[]}");

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

        // WHEN: Metadata service được khởi tạo.
        YoutubeVideoMetadataService actualService =
                new YoutubeVideoMetadataService(configuration);

        // THEN: Constructor không được ném exception.
        assertThat(actualService).isNotNull();
    }

    @Test
    void constructor_createsMetadataService_whenTimeoutIsNegative() {
        // GIVEN: Timeout âm không thể dùng trực tiếp.
        VideoAiProperties configuration = new VideoAiProperties();
        configuration.setYoutubeApiTimeout(Duration.ofSeconds(-1));

        // WHEN: Metadata service được khởi tạo.
        YoutubeVideoMetadataService actualService =
                new YoutubeVideoMetadataService(configuration);

        // THEN: Constructor phải fallback về timeout mặc định.
        assertThat(actualService).isNotNull();
    }

    @Test
    void constructor_createsMetadataService_whenTimeoutIsNull() {
        // GIVEN: Cấu hình không cung cấp timeout.
        VideoAiProperties configuration = new VideoAiProperties();
        configuration.setYoutubeApiTimeout(null);

        // WHEN: Metadata service được khởi tạo.
        YoutubeVideoMetadataService actualService =
                new YoutubeVideoMetadataService(configuration);

        // THEN: Constructor phải dùng timeout mặc định.
        assertThat(actualService).isNotNull();
    }

    @Test
    void constructor_createsMetadataService_whenTimeoutIsPositive() {
        // GIVEN: Timeout 5 giây là hợp lệ.
        VideoAiProperties configuration = new VideoAiProperties();
        configuration.setYoutubeApiTimeout(Duration.ofSeconds(5));

        // WHEN: Metadata service được khởi tạo.
        YoutubeVideoMetadataService actualService =
                new YoutubeVideoMetadataService(configuration);

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
                .extracting(exception ->
                        ((BusinessException) exception).errorCode())
                .isEqualTo(expectedErrorCode);
    }
}
