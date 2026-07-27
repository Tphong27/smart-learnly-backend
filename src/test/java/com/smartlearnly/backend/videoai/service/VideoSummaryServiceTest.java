package com.smartlearnly.backend.videoai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.videoai.dto.VideoAiDtos.GeneratedSummary;
import com.smartlearnly.backend.videoai.dto.VideoAiDtos.GenerateSummaryResponse;
import com.smartlearnly.backend.videoai.service.YoutubeVideoMetadataService.YoutubeVideoMetadata;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit test cho {@link VideoSummaryService}.
 *
 * <p>Class này chỉ kiểm tra ba trách nhiệm của VideoSummaryService:
 * đọc video ID từ URL, chuẩn hóa URL lesson và điều phối luồng tạo summary.
 * Các điều kiện metadata, transcript và Gemini được test trong service riêng.
 */
class VideoSummaryServiceTest {

    private static final String VIDEO_ID = "V9i3cGD-mts";
    private static final String WATCH_URL =
            "https://www.youtube.com/watch?v=" + VIDEO_ID;
    private static final GeneratedSummary GENERATED_SUMMARY =
            new GeneratedSummary(
                    List.of(
                            "Đoạn tổng quan thứ nhất.",
                            "Đoạn tổng quan thứ hai.",
                            "Đoạn tổng quan thứ ba."),
                    "Điểm chính",
                    List.of(
                            "Ý chính thứ nhất",
                            "Ý chính thứ hai",
                            "Ý chính thứ ba"));

    private YoutubeVideoMetadataService metadataService;
    private YoutubeTranscriptService transcriptService;
    private GeminiVideoSummaryService summaryService;
    private VideoSummaryService service;

    @BeforeEach
    void setUp() {
        // GIVEN: Các dependency được mock để mỗi test chỉ kiểm tra luồng điều phối.
        metadataService = mock(YoutubeVideoMetadataService.class);
        transcriptService = mock(YoutubeTranscriptService.class);
        summaryService = mock(GeminiVideoSummaryService.class);

        // GIVEN: Service cần kiểm thử nhận đúng ba dependency của luồng summary.
        service = new VideoSummaryService(
                metadataService,
                transcriptService,
                summaryService);
    }

    // -------------------------------------------------------------------------
    // extractVideoIdFromYoutubeUrl(): URL hợp lệ
    // -------------------------------------------------------------------------

    @Test
    void extractVideoIdFromYoutubeUrl_returnsVideoId_whenUrlIsStandardWatchUrl() {
        // GIVEN: URL HTTPS dạng youtube.com/watch?v= với video ID hợp lệ.
        String youtubeUrl = WATCH_URL;

        // WHEN: Service lấy video ID từ URL.
        String actualVideoId =
                service.extractVideoIdFromYoutubeUrl(youtubeUrl);

        // THEN: Video ID phải giống giá trị của query parameter "v".
        assertThat(actualVideoId).isEqualTo(VIDEO_ID);
    }

    @Test
    void extractVideoIdFromYoutubeUrl_returnsVideoId_whenWatchUrlHasNoWwwPrefix() {
        // GIVEN: URL dùng youtube.com nhưng không có tiền tố www.
        String youtubeUrl =
                "https://youtube.com/watch?v=" + VIDEO_ID;

        // WHEN: Service lấy video ID từ URL.
        String actualVideoId =
                service.extractVideoIdFromYoutubeUrl(youtubeUrl);

        // THEN: Host youtube.com phải được chấp nhận.
        assertThat(actualVideoId).isEqualTo(VIDEO_ID);
    }

    @Test
    void extractVideoIdFromYoutubeUrl_returnsVideoId_whenUrlUsesHttpsPort443() {
        // GIVEN: URL HTTPS khai báo rõ cổng mặc định 443.
        String youtubeUrl =
                "https://www.youtube.com:443/watch?v=" + VIDEO_ID;

        // WHEN: Service đọc video ID.
        String actualVideoId =
                service.extractVideoIdFromYoutubeUrl(youtubeUrl);

        // THEN: Cổng HTTPS 443 phải được chấp nhận.
        assertThat(actualVideoId).isEqualTo(VIDEO_ID);
    }

    @Test
    void extractVideoIdFromYoutubeUrl_returnsVideoId_whenUrlIsYoutuBeUrl() {
        // GIVEN: URL YouTube rút gọn dạng youtu.be/{videoId}.
        String youtubeUrl = "https://youtu.be/" + VIDEO_ID;

        // WHEN: Service đọc video ID từ path.
        String actualVideoId =
                service.extractVideoIdFromYoutubeUrl(youtubeUrl);

        // THEN: ID trong path phải được trả về.
        assertThat(actualVideoId).isEqualTo(VIDEO_ID);
    }

    // -------------------------------------------------------------------------
    // extractVideoIdFromYoutubeUrl(): URL không được hỗ trợ
    // -------------------------------------------------------------------------

    @Test
    void extractVideoIdFromYoutubeUrl_throwsInvalidRequest_whenUrlIsYoutubeShortsUrl() {
        // GIVEN: URL YouTube Shorts không thuộc hai định dạng được hỗ trợ.
        String youtubeUrl =
                "https://www.youtube.com/shorts/" + VIDEO_ID;

        // WHEN: Service cố đọc video ID.
        Throwable actualException = catchThrowable(
                () -> service.extractVideoIdFromYoutubeUrl(youtubeUrl));

        // THEN: URL Shorts phải bị từ chối bằng INVALID_REQUEST.
        assertInvalidYoutubeUrl(actualException);
    }

    @Test
    void extractVideoIdFromYoutubeUrl_throwsInvalidRequest_whenUrlIsYoutubeEmbedUrl() {
        // GIVEN: URL nhúng /embed/ không được hỗ trợ.
        String youtubeUrl =
                "https://www.youtube.com/embed/" + VIDEO_ID;

        // WHEN: Service cố đọc video ID.
        Throwable actualException = catchThrowable(
                () -> service.extractVideoIdFromYoutubeUrl(youtubeUrl));

        // THEN: URL embed phải bị từ chối.
        assertInvalidYoutubeUrl(actualException);
    }

    @Test
    void extractVideoIdFromYoutubeUrl_throwsInvalidRequest_whenUrlUsesMobileYoutubeHost() {
        // GIVEN: URL sử dụng host m.youtube.com.
        String youtubeUrl =
                "https://m.youtube.com/watch?v=" + VIDEO_ID;

        // WHEN: Service kiểm tra host.
        Throwable actualException = catchThrowable(
                () -> service.extractVideoIdFromYoutubeUrl(youtubeUrl));

        // THEN: Host mobile không nằm trong danh sách hỗ trợ.
        assertInvalidYoutubeUrl(actualException);
    }

    @Test
    void extractVideoIdFromYoutubeUrl_throwsInvalidRequest_whenUrlUsesYoutubeMusicHost() {
        // GIVEN: URL sử dụng host music.youtube.com.
        String youtubeUrl =
                "https://music.youtube.com/watch?v=" + VIDEO_ID;

        // WHEN: Service kiểm tra host.
        Throwable actualException = catchThrowable(
                () -> service.extractVideoIdFromYoutubeUrl(youtubeUrl));

        // THEN: YouTube Music không thuộc luồng video lesson.
        assertInvalidYoutubeUrl(actualException);
    }

    @Test
    void extractVideoIdFromYoutubeUrl_throwsInvalidRequest_whenUrlUsesHttp() {
        // GIVEN: URL sử dụng HTTP thay vì HTTPS.
        String youtubeUrl =
                "http://www.youtube.com/watch?v=" + VIDEO_ID;

        // WHEN: Service kiểm tra scheme.
        Throwable actualException = catchThrowable(
                () -> service.extractVideoIdFromYoutubeUrl(youtubeUrl));

        // THEN: URL không an toàn phải bị từ chối.
        assertInvalidYoutubeUrl(actualException);
    }

    @Test
    void extractVideoIdFromYoutubeUrl_throwsInvalidRequest_whenUrlIsNull() {
        // GIVEN: Người dùng không truyền URL.
        String youtubeUrl = null;

        // WHEN: Service chuẩn hóa URL null.
        Throwable actualException = catchThrowable(
                () -> service.extractVideoIdFromYoutubeUrl(youtubeUrl));

        // THEN: URL bắt buộc phải có giá trị.
        assertInvalidYoutubeUrl(actualException);
    }

    @Test
    void extractVideoIdFromYoutubeUrl_throwsInvalidRequest_whenUrlIsBlank() {
        // GIVEN: URL chỉ chứa khoảng trắng.
        String youtubeUrl = "   ";

        // WHEN: Service loại bỏ khoảng trắng.
        Throwable actualException = catchThrowable(
                () -> service.extractVideoIdFromYoutubeUrl(youtubeUrl));

        // THEN: Chuỗi rỗng sau khi trim phải bị từ chối.
        assertInvalidYoutubeUrl(actualException);
    }

    @Test
    void extractVideoIdFromYoutubeUrl_throwsInvalidRequest_whenValueIsNotUrl() {
        // GIVEN: Đầu vào là văn bản thông thường.
        String youtubeUrl = "this-is-not-a-url";

        // WHEN: Service phân tích URI.
        Throwable actualException = catchThrowable(
                () -> service.extractVideoIdFromYoutubeUrl(youtubeUrl));

        // THEN: Văn bản không phải URL YouTube phải bị từ chối.
        assertInvalidYoutubeUrl(actualException);
    }

    @Test
    void extractVideoIdFromYoutubeUrl_throwsInvalidRequest_whenYoutuBeUrlHasNoVideoId() {
        // GIVEN: URL youtu.be không có video ID trong path.
        String youtubeUrl = "https://youtu.be/";

        // WHEN: Service đọc path.
        Throwable actualException = catchThrowable(
                () -> service.extractVideoIdFromYoutubeUrl(youtubeUrl));

        // THEN: URL thiếu video ID phải bị từ chối.
        assertInvalidYoutubeUrl(actualException);
    }

    @Test
    void extractVideoIdFromYoutubeUrl_throwsInvalidRequest_whenVideoIdLengthIsInvalid() {
        // GIVEN: Query parameter v không có đúng 11 ký tự.
        String youtubeUrl =
                "https://youtube.com/watch?v=short";

        // WHEN: Service kiểm tra định dạng video ID.
        Throwable actualException = catchThrowable(
                () -> service.extractVideoIdFromYoutubeUrl(youtubeUrl));

        // THEN: Video ID sai độ dài phải bị từ chối.
        assertInvalidYoutubeUrl(actualException);
    }

    // -------------------------------------------------------------------------
    // normalizeLessonVideoUrl()
    // -------------------------------------------------------------------------

    @Test
    void normalizeLessonVideoUrl_returnsCanonicalUrl_whenNewYoutuBeUrlIsProvided() {
        // GIVEN: Video lesson mới nhận URL rút gọn youtu.be.
        String requestedVideoUrl = "https://youtu.be/" + VIDEO_ID;

        // WHEN: Service chuẩn hóa URL trước khi lưu.
        String actualUrl = service.normalizeLessonVideoUrl(
                null,
                requestedVideoUrl,
                true);

        // THEN: URL được chuyển thành dạng youtube.com/watch?v= chuẩn.
        assertThat(actualUrl).isEqualTo(WATCH_URL);
    }

    @Test
    void normalizeLessonVideoUrl_keepsCurrentUrl_whenRequestedUrlIsNull() {
        // GIVEN: Lesson đã có URL và request không gửi URL thay thế.
        String currentVideoUrl = WATCH_URL;

        // WHEN: Service chuẩn hóa dữ liệu update.
        String actualUrl = service.normalizeLessonVideoUrl(
                currentVideoUrl,
                null,
                true);

        // THEN: URL hiện tại phải được giữ nguyên.
        assertThat(actualUrl).isEqualTo(currentVideoUrl);
    }

    @Test
    void normalizeLessonVideoUrl_keepsCurrentUrl_whenRequestedUrlIsUnchanged() {
        // GIVEN: URL hiện tại và URL request giống nhau.
        String currentVideoUrl = WATCH_URL;

        // WHEN: Service kiểm tra URL.
        String actualUrl = service.normalizeLessonVideoUrl(
                currentVideoUrl,
                currentVideoUrl,
                true);

        // THEN: Service không thay đổi URL.
        assertThat(actualUrl).isEqualTo(currentVideoUrl);
    }

    @Test
    void normalizeLessonVideoUrl_returnsNull_whenLessonIsNotVideoLesson() {
        // GIVEN: Lesson không phải loại video dù request có gửi URL.
        boolean videoLesson = false;

        // WHEN: Service chuẩn hóa URL theo loại lesson.
        String actualUrl = service.normalizeLessonVideoUrl(
                null,
                WATCH_URL,
                videoLesson);

        // THEN: Lesson không phải video không được lưu video URL.
        assertThat(actualUrl).isNull();
    }

    @Test
    void normalizeLessonVideoUrl_throwsInvalidRequest_whenNewVideoLessonHasNoUrl() {
        // GIVEN: Video lesson mới không có URL hiện tại hoặc URL mới.
        String currentVideoUrl = null;
        String requestedVideoUrl = null;

        // WHEN: Service kiểm tra URL bắt buộc.
        Throwable actualException = catchThrowable(
                () -> service.normalizeLessonVideoUrl(
                        currentVideoUrl,
                        requestedVideoUrl,
                        true));

        // THEN: Video lesson thiếu URL phải bị từ chối.
        assertInvalidYoutubeUrl(actualException);
    }

    // -------------------------------------------------------------------------
    // generateVideoSummary(): chỉ kiểm tra việc điều phối các service
    // -------------------------------------------------------------------------

    @Test
    void generateVideoSummary_returnsCompleteResponse_whenAllServicesSucceed() {
        // GIVEN: Metadata cho biết video dài 17 phút 1 giây.
        when(metadataService.fetchYoutubeVideoMetadata(VIDEO_ID))
                .thenReturn(new YoutubeVideoMetadata(1_021));

        // GIVEN: Transcript service trả ngôn ngữ và nội dung phụ đề.
        when(transcriptService.fetchYoutubeTranscript(VIDEO_ID))
                .thenReturn(new YoutubeTranscriptService.TranscriptResult(
                        "vi",
                        "Nội dung bài học"));

        // GIVEN: Gemini trả bản tóm tắt có cấu trúc.
        when(summaryService.generateSummaryFromTranscript(
                "vi",
                "Nội dung bài học"))
                .thenReturn(GENERATED_SUMMARY);

        // WHEN: Người dùng tạo summary từ URL youtu.be hợp lệ.
        GenerateSummaryResponse actualResponse =
                service.generateVideoSummary(
                        "https://youtu.be/" + VIDEO_ID);

        // THEN: Response chứa đúng ID, URL chuẩn và summary.
        assertThat(actualResponse.videoId()).isEqualTo(VIDEO_ID);
        assertThat(actualResponse.videoUrl()).isEqualTo(WATCH_URL);
        assertThat(actualResponse.summary()).isEqualTo(GENERATED_SUMMARY);

        // THEN: 17 phút 1 giây được làm tròn lên 18 phút.
        assertThat(actualResponse.durationMinutes()).isEqualTo(18);

        // THEN: Ba service phải được gọi đúng thứ tự dữ liệu của luồng.
        verify(metadataService).fetchYoutubeVideoMetadata(VIDEO_ID);
        verify(transcriptService).fetchYoutubeTranscript(VIDEO_ID);
        verify(summaryService)
                .generateSummaryFromTranscript(
                        "vi",
                        "Nội dung bài học");
    }

    @Test
    void generateVideoSummary_propagatesException_whenMetadataValidationFails() {
        // GIVEN: YouTube metadata từ chối video vì không có caption.
        when(metadataService.fetchYoutubeVideoMetadata(VIDEO_ID))
                .thenThrow(new BusinessException(
                        ErrorCode.BUSINESS_RULE_VIOLATION,
                        "This YouTube video does not have captions"));

        // WHEN: Người dùng yêu cầu tạo summary.
        Throwable actualException = catchThrowable(
                () -> service.generateVideoSummary(WATCH_URL));

        // THEN: generateVideoSummary giữ nguyên error code từ metadata service.
        assertThat(actualException)
                .isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);

        // THEN: Luồng phải dừng trước bước lấy transcript.
        verify(transcriptService, never())
                .fetchYoutubeTranscript(VIDEO_ID);
    }

    @Test
    void generateVideoSummary_propagatesException_whenTranscriptRetrievalFails() {
        // GIVEN: Metadata hợp lệ.
        when(metadataService.fetchYoutubeVideoMetadata(VIDEO_ID))
                .thenReturn(new YoutubeVideoMetadata(600));

        // GIVEN: YouTube không có transcript khả dụng.
        when(transcriptService.fetchYoutubeTranscript(VIDEO_ID))
                .thenThrow(new BusinessException(
                        ErrorCode.BUSINESS_RULE_VIOLATION,
                        "This YouTube video does not have an available transcript"));

        // WHEN: Người dùng yêu cầu tạo summary.
        Throwable actualException = catchThrowable(
                () -> service.generateVideoSummary(WATCH_URL));

        // THEN: generateVideoSummary giữ nguyên lỗi của transcript service.
        assertThat(actualException)
                .isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);

        // THEN: Không được gọi Gemini khi chưa lấy được transcript.
        verify(summaryService, never())
                .generateSummaryFromTranscript(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void generateVideoSummary_propagatesException_whenGeminiGenerationFails() {
        // GIVEN: Metadata và transcript đều hợp lệ.
        when(metadataService.fetchYoutubeVideoMetadata(VIDEO_ID))
                .thenReturn(new YoutubeVideoMetadata(600));
        when(transcriptService.fetchYoutubeTranscript(VIDEO_ID))
                .thenReturn(new YoutubeTranscriptService.TranscriptResult(
                        "en",
                        "Lesson transcript"));

        // GIVEN: Gemini tạm thời không hoạt động.
        when(summaryService.generateSummaryFromTranscript(
                "en",
                "Lesson transcript"))
                .thenThrow(new BusinessException(
                        ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                        "AI summary generation is temporarily unavailable"));

        // WHEN: Service gửi transcript sang Gemini.
        Throwable actualException = catchThrowable(
                () -> service.generateVideoSummary(WATCH_URL));

        // THEN: Lỗi Gemini phải được truyền nguyên vẹn cho caller.
        assertThat(actualException)
                .isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
    }

    private void assertInvalidYoutubeUrl(Throwable actualException) {
        assertThat(actualException)
                .isInstanceOf(BusinessException.class)
                .hasMessage("Enter a valid HTTPS YouTube URL")
                .extracting(exception ->
                        ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }
}
