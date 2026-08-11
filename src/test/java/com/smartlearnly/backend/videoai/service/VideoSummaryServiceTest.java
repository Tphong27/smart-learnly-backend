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
 * <p>
 * Class này chỉ kiểm tra ba trách nhiệm của VideoSummaryService:
 * đọc video ID từ URL, chuẩn hóa URL lesson và điều phối luồng tạo summary.
 * Các điều kiện metadata, transcript và Gemini được test trong service riêng.
 */
class VideoSummaryServiceTest {

        private static final String VIDEO_ID = "V9i3cGD-mts";
        private static final String WATCH_URL = "https://www.youtube.com/watch?v=" + VIDEO_ID;
        private static final GeneratedSummary GENERATED_SUMMARY = new GeneratedSummary(
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
                String actualVideoId = service.extractVideoIdFromYoutubeUrl(youtubeUrl);

                // THEN: Video ID phải giống giá trị của query parameter "v".
                assertThat(actualVideoId).isEqualTo(VIDEO_ID);
        }

        @Test
        void extractVideoIdFromYoutubeUrl_returnsVideoId_whenWatchUrlHasNoWwwPrefix() {
                // GIVEN: URL dùng youtube.com nhưng không có tiền tố www.
                String youtubeUrl = "https://youtube.com/watch?v=" + VIDEO_ID;

                // WHEN: Service lấy video ID từ URL.
                String actualVideoId = service.extractVideoIdFromYoutubeUrl(youtubeUrl);

                // THEN: Host youtube.com phải được chấp nhận.
                assertThat(actualVideoId).isEqualTo(VIDEO_ID);
        }

        @Test
        void extractVideoIdFromYoutubeUrl_returnsVideoId_whenUrlUsesHttpsPort443() {
                // GIVEN: URL HTTPS khai báo rõ cổng mặc định 443.
                String youtubeUrl = "https://www.youtube.com:443/watch?v=" + VIDEO_ID;

                // WHEN: Service đọc video ID.
                String actualVideoId = service.extractVideoIdFromYoutubeUrl(youtubeUrl);

                // THEN: Cổng HTTPS 443 phải được chấp nhận.
                assertThat(actualVideoId).isEqualTo(VIDEO_ID);
        }

        @Test
        void extractVideoIdFromYoutubeUrl_returnsVideoId_whenUrlIsYoutuBeUrl() {
                // GIVEN: URL YouTube rút gọn dạng youtu.be/{videoId}.
                String youtubeUrl = "https://youtu.be/" + VIDEO_ID;

                /*
                 * DEBUG FLOW:
                 * 1. Dòng 92: host="youtu.be", path="/V9i3cGD-mts", port=-1.
                 * 2. Dòng 93-96: URL HTTPS hợp lệ nên if=false.
                 * 3. Dòng 106: "youtu.be".equals(host)=true.
                 * 4. Dòng 107: parts=["", "V9i3cGD-mts"], parts.length=2.
                 * 5. Dòng 109: videoId=parts[1]="V9i3cGD-mts".
                 * 6. Dòng 120 regex=true; dòng 125 trả VIDEO_ID.
                 */
                // WHEN: Service đọc video ID từ path.
                String actualVideoId = service.extractVideoIdFromYoutubeUrl(youtubeUrl);

                // THEN: ID trong path phải được trả về.
                assertThat(actualVideoId).isEqualTo(VIDEO_ID);
        }

        /**
         * CODE UNDER TEST: VideoSummaryService.java dòng 111-117, nhánh path bằng
         * "/watch/".
         * Biến vào: youtubeUrl = https://www.youtube.com/watch/?v={VIDEO_ID}.
         * Mục tiêu: đi qua nhánh else-if của host youtube.com với dấu / cuối path.
         */
        @Test
        void extractVideoIdFromYoutubeUrl_returnsVideoId_whenWatchPathHasTrailingSlash() {
                // GIVEN - variable: URL có path "/watch/" và query parameter v hợp lệ.
                String youtubeUrl = "https://www.youtube.com/watch/?v=" + VIDEO_ID;

                /*
                 * DEBUG FLOW:
                 * 1. Dòng 92-103: host="www.youtube.com", path="/watch/".
                 * 2. Dòng 111-112: host hợp lệ=true.
                 * 3. Dòng 113: /watch=false nhưng /watch/=true; cả nhóm path=true.
                 * 4. Dòng 114-117: videoId lấy từ query v=VIDEO_ID.
                 * 5. Dòng 120 không throw; dòng 125 return VIDEO_ID.
                 */
                // WHEN - code line: gọi extractVideoIdFromYoutubeUrl để vào nhánh "/watch/".
                String actualVideoId = service.extractVideoIdFromYoutubeUrl(youtubeUrl);

                // THEN - expected: nhánh else-if đọc đúng VIDEO_ID.
                assertThat(actualVideoId).isEqualTo(VIDEO_ID);
        }

        // -------------------------------------------------------------------------
        // extractVideoIdFromYoutubeUrl(): URL không được hỗ trợ
        // -------------------------------------------------------------------------

        @Test
        void extractVideoIdFromYoutubeUrl_throwsInvalidRequest_whenUrlIsYoutubeShortsUrl() {
                String youtubeUrl = "https://www.youtube.com/shorts/" + VIDEO_ID;
                Throwable actualException = catchThrowable(
                                () -> service.extractVideoIdFromYoutubeUrl(youtubeUrl));
                assertInvalidYoutubeUrl(actualException);
        }

        @Test
        void extractVideoIdFromYoutubeUrl_throwsInvalidRequest_whenUrlIsYoutubeEmbedUrl() {
                String youtubeUrl = "https://www.youtube.com/embed/" + VIDEO_ID;

                Throwable actualException = catchThrowable(
                                () -> service.extractVideoIdFromYoutubeUrl(youtubeUrl));

                assertInvalidYoutubeUrl(actualException);
        }

        @Test
        void extractVideoIdFromYoutubeUrl_throwsInvalidRequest_whenUrlUsesMobileYoutubeHost() {
                // GIVEN: URL sử dụng host m.youtube.com.
                String youtubeUrl = "https://m.youtube.com/watch?v=" + VIDEO_ID;

                /*
                 * DEBUG FLOW:
                 * 1. Dòng 92-103: host="m.youtube.com", path="/watch", videoId=null.
                 * 2. Kiểm tra an toàn dòng 93-96=false vì URL vẫn là HTTPS hợp lệ.
                 * 3. Dòng 106: host youtu.be=false.
                 * 4. Dòng 111-112: host youtube.com=false và www.youtube.com=false;
                 * do short-circuit, nhóm path không quyết định được nhánh.
                 * 5. Dòng 120 videoId==null=true; dòng 121 ném INVALID_REQUEST.
                 * 6. catchThrowable gán BusinessException vào actualException.
                 */
                // WHEN: Service kiểm tra host.
                Throwable actualException = catchThrowable(
                                () -> service.extractVideoIdFromYoutubeUrl(youtubeUrl));

                // THEN: Host mobile không nằm trong danh sách hỗ trợ.
                assertInvalidYoutubeUrl(actualException);
        }

        @Test
        void extractVideoIdFromYoutubeUrl_throwsInvalidRequest_whenUrlUsesYoutubeMusicHost() {
                // GIVEN: URL sử dụng host music.youtube.com.
                String youtubeUrl = "https://music.youtube.com/watch?v=" + VIDEO_ID;

                // WHEN: Service kiểm tra host.
                Throwable actualException = catchThrowable(
                                () -> service.extractVideoIdFromYoutubeUrl(youtubeUrl));

                // THEN: YouTube Music không thuộc luồng video lesson.
                assertInvalidYoutubeUrl(actualException);
        }

        @Test
        void extractVideoIdFromYoutubeUrl_throwsInvalidRequest_whenUrlUsesHttp() {
                // GIVEN: URL sử dụng HTTP thay vì HTTPS.
                String youtubeUrl = "http://www.youtube.com/watch?v=" + VIDEO_ID;

                // WHEN: Service kiểm tra scheme.
                Throwable actualException = catchThrowable(
                                () -> service.extractVideoIdFromYoutubeUrl(youtubeUrl));

                // THEN: URL không an toàn phải bị từ chối.
                assertInvalidYoutubeUrl(actualException);
        }

        @Test
        void extractVideoIdFromYoutubeUrl_throwsInvalidRequest_whenHttpsUrlHasNoHost() {
                // GIVEN - variable: URI hợp cú pháp nhưng host = null.
                String youtubeUrl = "https:/watch?v=" + VIDEO_ID;

                /*
                 * DEBUG FLOW:
                 * 1. Dòng 92 tạo URI hợp cú pháp với scheme="https", host=null,
                 * path="/watch".
                 * 2. Dòng 93 kiểm tra scheme sai=false.
                 * 3. Dòng 94 kiểm tra host==null=true; các vế sau của || không chạy.
                 * 4. Dòng 97 ném BusinessException(INVALID_REQUEST).
                 * 5. BusinessException truyền ra ngoài và được catchThrowable gán vào
                 * actualException; helper xác minh code/message.
                 */
                // WHEN - code line: kiểm tra scheme/host của URI.
                Throwable actualException = catchThrowable(
                                () -> service.extractVideoIdFromYoutubeUrl(youtubeUrl));

                // THEN - expected: nhánh host == null trả INVALID_REQUEST.
                assertInvalidYoutubeUrl(actualException);
        }

        /**
         * CODE UNDER TEST: VideoSummaryService.java dòng 93-96,
         * điều kiện uri.getUserInfo() != null.
         * Biến vào: youtubeUrl chứa user-info "student@" trước host YouTube.
         * Mục tiêu: từ chối URL có thông tin đăng nhập dù host và video ID hợp lệ.
         */
        @Test
        void extractVideoIdFromYoutubeUrl_throwsInvalidRequest_whenUrlContainsUserInfo() {
                // GIVEN - variable: userInfo = "student", host = "www.youtube.com".
                String youtubeUrl = "https://student@www.youtube.com/watch?v=" + VIDEO_ID;

                /*
                 * DEBUG FLOW:
                 * 1. URI tại dòng 92 có scheme=https, host=www.youtube.com,
                 * userInfo="student", port=-1.
                 * 2. Dòng 93=false; dòng 94=false; dòng 95 userInfo!=null=true.
                 * 3. Điều kiện || dừng tại dòng 95; dòng 97 ném INVALID_REQUEST.
                 * 4. Không đi tới phần đọc host/path/videoId dòng 102-125.
                 * 5. catchThrowable bắt exception; helper kiểm tra type, code và message.
                 */
                // WHEN - code line: kiểm tra uri.getUserInfo().
                Throwable actualException = catchThrowable(
                                () -> service.extractVideoIdFromYoutubeUrl(youtubeUrl));

                // THEN - expected: nhánh userInfo != null trả INVALID_REQUEST.
                assertInvalidYoutubeUrl(actualException);
        }

        /**
         * CODE UNDER TEST: VideoSummaryService.java dòng 93-96,
         * điều kiện port != -1 && port != 443.
         * Biến vào: port = 8443.
         * Mục tiêu: đi qua vế true của cả hai điều kiện kiểm tra cổng HTTPS.
         */
        @Test
        void extractVideoIdFromYoutubeUrl_throwsInvalidRequest_whenUrlUsesNonHttpsPort() {
                // GIVEN - variable: URL YouTube hợp lệ về host nhưng dùng port 8443.
                String youtubeUrl = "https://www.youtube.com:8443/watch?v=" + VIDEO_ID;

                /*
                 * DEBUG FLOW:
                 * 1. URI: scheme=https, host=www.youtube.com, userInfo=null, port=8443.
                 * 2. Dòng 93-95 đều false.
                 * 3. Dòng 96: port!=-1=true và port!=443=true => cả biểu thức true.
                 * 4. Dòng 97 ném BusinessException(INVALID_REQUEST), không đọc videoId.
                 * 5. catchThrowable nhận exception; assertInvalidYoutubeUrl kiểm tra nó.
                 */
                // WHEN - code line: kiểm tra cổng của URI.
                Throwable actualException = catchThrowable(
                                () -> service.extractVideoIdFromYoutubeUrl(youtubeUrl));

                // THEN - expected: cổng khác 443 bị từ chối bằng INVALID_REQUEST.
                assertInvalidYoutubeUrl(actualException);
        }

        /**
         * CODE UNDER TEST: VideoSummaryService.java dòng 126-129, catch
         * URISyntaxException | IllegalArgumentException.
         * Biến vào: youtubeUrl có escape sequence "%ZZ" không hợp lệ.
         * Mục tiêu: chứng minh lỗi parse URI được catch và chuyển thành
         * BusinessException.
         */
        @Test
        void extractVideoIdFromYoutubeUrl_throwsInvalidRequest_whenUriSyntaxIsMalformed() {
                // GIVEN - variable: query chứa phần trăm escape không phải hexadecimal.
                String youtubeUrl = "https://www.youtube.com/watch?v=%ZZ";

                // WHEN - code line: new URI(...) ném URISyntaxException và đi vào catch.
                Throwable actualException = catchThrowable(
                                () -> service.extractVideoIdFromYoutubeUrl(youtubeUrl));

                // THEN - expected: catch che lỗi kỹ thuật và trả INVALID_REQUEST ổn định.
                assertInvalidYoutubeUrl(actualException);
        }

        @Test
        void extractVideoIdFromYoutubeUrl_throwsInvalidRequest_whenUrlIsNull() {
                // GIVEN: Người dùng không truyền URL.
                String youtubeUrl = null;

                /*
                 * DEBUG FLOW:
                 * 1. Dòng 84 gọi normalize(value=null).
                 * 2. Dòng 76: value==null=true; dòng 77 return null ngay.
                 * 3. normalized=null; dòng 85 normalized==null=true.
                 * 4. Dòng 86 ném BusinessException(INVALID_REQUEST) trước khối try.
                 * 5. Không tạo URI; catch dòng 126 không tham gia.
                 * 6. catchThrowable bắt BusinessException vào actualException.
                 */
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

                /*
                 * DEBUG FLOW:
                 * 1. Dòng 76 value==null=false.
                 * 2. Dòng 79: normalized=value.trim()="".
                 * 3. Dòng 80: normalized.isEmpty()=true => normalize() return null.
                 * 4. Dòng 85=true; dòng 86 ném BusinessException(INVALID_REQUEST).
                 * 5. Khối try chưa chạy; catchThrowable thu exception để assertion kiểm tra.
                 */
                // WHEN: Service loại bỏ khoảng trắng.
                Throwable actualException = catchThrowable(
                                () -> service.extractVideoIdFromYoutubeUrl(youtubeUrl));

                // THEN: Chuỗi rỗng sau khi trim phải bị từ chối.
                assertInvalidYoutubeUrl(actualException);
        }

        @Test
        void extractVideoIdFromYoutubeUrl_throwsInvalidRequest_whenVideoIdLengthIsInvalid() {
                // GIVEN: Query parameter v không có đúng 11 ký tự.
                String youtubeUrl = "https://youtube.com/watch?v=short";

                /*
                 * DEBUG FLOW:
                 * 1. URI hợp lệ: host=youtube.com, path=/watch.
                 * 2. Dòng 111-117 vào else-if và gán videoId="short".
                 * 3. Dòng 120: videoId==null=false; videoId.matches(regex)=false vì
                 * "short" chỉ có 5 ký tự thay vì 11.
                 * 4. false || true=true; dòng 121 ném BusinessException(INVALID_REQUEST).
                 * 5. catchThrowable nhận exception; helper kiểm tra code/message.
                 */
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

                /*
                 * DEBUG FLOW:
                 * 1. Tham số: currentVideoUrl=null, requestedVideoUrl=youtu.be URL,
                 * videoLesson=true.
                 * 2. Dòng 54 !videoLesson=false.
                 * 3. Dòng 57: current=normalize(null)=null; dòng 58 requested=URL.
                 * 4. Dòng 59 requested==null=false; dòng 68 current!=null=false.
                 * 5. Dòng 72 extract URL rút gọn => videoId=VIDEO_ID.
                 * 6. Dòng 71 build canonical và return WATCH_URL.
                 */
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

                /*
                 * DEBUG FLOW:
                 * 1. Tham số: currentVideoUrl=WATCH_URL, requestedVideoUrl=null,
                 * videoLesson=true.
                 * 2. Dòng 54=false; dòng 57 current=WATCH_URL; dòng 58 requested=null.
                 * 3. Dòng 59 requested==null=true.
                 * 4. Dòng 60 current!=null=true; dòng 61 return current ngay.
                 * 5. Dòng 68-72 không chạy; không parse lại URL và không có exception.
                 */
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

                /*
                 * DEBUG FLOW:
                 * 1. currentVideoUrl=requestedVideoUrl=WATCH_URL, videoLesson=true.
                 * 2. Dòng 57-58: current và requested đều là WATCH_URL sau trim.
                 * 3. Dòng 59 requested==null=false.
                 * 4. Dòng 68: current!=null=true và current.equals(requested)=true.
                 * 5. Dòng 69 return current; extractVideoIdFromYoutubeUrl không được gọi.
                 */
                // WHEN: Service kiểm tra URL.
                String actualUrl = service.normalizeLessonVideoUrl(
                                currentVideoUrl,
                                currentVideoUrl,
                                true);

                // THEN: Service không thay đổi URL.
                assertThat(actualUrl).isEqualTo(currentVideoUrl);
        }

        /**
         * CODE UNDER TEST: VideoSummaryService.java dòng 68-72, điều kiện
         * current != null && current.equals(requested) với equals = false.
         * Biến vào: currentVideoUrl và requestedVideoUrl khác nhau.
         * Mục tiêu: không giữ URL cũ; URL mới phải được parse và canonicalize.
         */
        @Test
        void normalizeLessonVideoUrl_returnsCanonicalRequestedUrl_whenCurrentUrlIsDifferent() {
                // GIVEN - variables: current khác requested, lesson vẫn là video.
                String currentVideoUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
                String requestedVideoUrl = "https://youtu.be/" + VIDEO_ID;
                boolean videoLesson = true;

                /*
                 * DEBUG FLOW:
                 * 1. current chứa ID dQw4w9WgXcQ; requested chứa ID V9i3cGD-mts.
                 * 2. Dòng 57-58 normalize hai URL, cả hai khác null.
                 * 3. Dòng 59=false; dòng 68 current!=null=true nhưng equals=false.
                 * 4. Không return current; dòng 72 parse requested => VIDEO_ID.
                 * 5. Dòng 71 buildCanonicalYoutubeUrl(VIDEO_ID) và trả WATCH_URL mới.
                 */
                // WHEN - code line: nhánh equals = false tiếp tục gọi
                // extractVideoIdFromYoutubeUrl.
                String actualUrl = service.normalizeLessonVideoUrl(
                                currentVideoUrl,
                                requestedVideoUrl,
                                videoLesson);

                // THEN - expected: dùng VIDEO_ID của requested, không trả currentVideoUrl.
                assertThat(actualUrl).isEqualTo(WATCH_URL);
        }

        @Test
        void normalizeLessonVideoUrl_returnsNull_whenLessonIsNotVideoLesson() {
                // GIVEN: Lesson không phải loại video dù request có gửi URL.
                boolean videoLesson = false;

                /*
                 * DEBUG FLOW:
                 * 1. Tham số videoLesson=false; URL request vẫn là WATCH_URL.
                 * 2. Dòng 54: !videoLesson=true.
                 * 3. Dòng 55 return null ngay; current/requested không được normalize.
                 * 4. Không gọi extractVideoIdFromYoutubeUrl và không ném exception.
                 */
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

                /*
                 * DEBUG FLOW:
                 * 1. currentVideoUrl=null, requestedVideoUrl=null, videoLesson=true.
                 * 2. Dòng 54=false; dòng 57 current=null; dòng 58 requested=null.
                 * 3. Dòng 59 requested==null=true.
                 * 4. Dòng 60 current!=null=false nên không return current.
                 * 5. Dòng 63 ném BusinessException(INVALID_REQUEST) với message URL.
                 * 6. catchThrowable bắt exception; helper kiểm tra đủ type/code/message.
                 */
                // WHEN: Service kiểm tra URL bắt buộc.
                Throwable actualException = catchThrowable(
                                () -> service.normalizeLessonVideoUrl(
                                                currentVideoUrl,
                                                requestedVideoUrl,
                                                true));

                // THEN: Video lesson thiếu URL phải bị từ chối.
                assertInvalidYoutubeUrl(actualException);
        }

        @Test
        void generateVideoSummary_returnsCompleteResponse_whenAllServicesSucceed() {
                // GIVEN: Metadata cho biết video dài 17 phút 1 giây.
                when(metadataService.fetchYoutubeVideoMetadata(VIDEO_ID))
                                .thenReturn(new YoutubeVideoMetadata(1_021));

                // GIVEN: Gemini đọc trực tiếp URL và trả bản tóm tắt có cấu trúc.
                when(summaryService.generateSummaryFromYoutubeVideo(WATCH_URL))
                                .thenReturn(GENERATED_SUMMARY);

                /*
                 * DEBUG FLOW:
                 * 1. Dòng 32 parse youtu.be URL => videoId="V9i3cGD-mts".
                 * 2. Dòng 34 gọi metadataService(videoId); mock trả durationSeconds=1021.
                 * 3. Service chuẩn hóa URL và gọi Gemini trực tiếp bằng WATCH_URL.
                 * 4. Gemini trả GENERATED_SUMMARY nên không chạy transcript fallback.
                 * 5. Thời lượng 1021 giây được làm tròn lên 18 phút.
                 */
                // WHEN: Người dùng tạo summary từ URL youtu.be hợp lệ.
                GenerateSummaryResponse actualResponse = service.generateVideoSummary(
                                "https://youtu.be/" + VIDEO_ID);

                // THEN: Response chứa đúng ID, URL chuẩn và summary.
                assertThat(actualResponse.videoId()).isEqualTo(VIDEO_ID);
                assertThat(actualResponse.videoUrl()).isEqualTo(WATCH_URL);
                assertThat(actualResponse.summary()).isEqualTo(GENERATED_SUMMARY);

                // THEN: Response giữ nguyên thời lượng chính xác theo giây.
                assertThat(actualResponse.durationSeconds()).isEqualTo(1_021L);

                // THEN: 17 phút 1 giây được làm tròn lên 18 phút.
                assertThat(actualResponse.durationMinutes()).isEqualTo(18);

                // THEN: Luồng chính không được gọi transcript scraper.
                verify(metadataService).fetchYoutubeVideoMetadata(VIDEO_ID);
                verify(summaryService).generateSummaryFromYoutubeVideo(WATCH_URL);
                verify(transcriptService, never()).fetchYoutubeTranscript(VIDEO_ID);
        }

        @Test
        void generateVideoSummary_propagatesException_whenMetadataValidationFails() {
                // GIVEN: YouTube metadata từ chối video vì không có caption.
                when(metadataService.fetchYoutubeVideoMetadata(VIDEO_ID))
                                .thenThrow(new BusinessException(
                                                ErrorCode.BUSINESS_RULE_VIOLATION,
                                                "This YouTube video does not have captions"));

                /*
                 * DEBUG FLOW:
                 * 1. Dòng 32 parse WATCH_URL thành videoId=VIDEO_ID.
                 * 2. Dòng 34 gọi metadataService.fetchYoutubeVideoMetadata(VIDEO_ID).
                 * 3. Mock ném BusinessException(BUSINESS_RULE_VIOLATION) ngay tại dòng 34.
                 * 4. generateVideoSummary không có try/catch nên exception truyền thẳng ra.
                 * 5. Dòng 35-47 không chạy; transcriptService và Gemini không được gọi.
                 * 6. catchThrowable bắt đúng exception mock vào actualException.
                 */
                // WHEN: Người dùng yêu cầu tạo summary.
                Throwable actualException = catchThrowable(
                                () -> service.generateVideoSummary(WATCH_URL));

                // THEN: generateVideoSummary giữ nguyên error code từ metadata service.
                assertThat(actualException)
                                .isInstanceOf(BusinessException.class)
                                .extracting(exception -> ((BusinessException) exception).errorCode())
                                .isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);

                // THEN: Luồng phải dừng trước bước lấy transcript.
                verify(transcriptService, never())
                                .fetchYoutubeTranscript(VIDEO_ID);
        }

        @Test
        void generateVideoSummary_fallsBackToTranscript_whenDirectGeminiFails() {
                // GIVEN: Metadata hợp lệ.
                when(metadataService.fetchYoutubeVideoMetadata(VIDEO_ID))
                                .thenReturn(new YoutubeVideoMetadata(600));

                // GIVEN: Gemini trực tiếp tạm lỗi nhưng transcript vẫn lấy được.
                when(summaryService.generateSummaryFromYoutubeVideo(WATCH_URL))
                                .thenThrow(new BusinessException(
                                                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                                                "Direct video input is temporarily unavailable"));
                when(transcriptService.fetchYoutubeTranscript(VIDEO_ID))
                                .thenReturn(new YoutubeTranscriptService.TranscriptResult(
                                                "vi",
                                                "Nội dung bài học"));
                when(summaryService.generateSummaryFromTranscript(
                                "vi",
                                "Nội dung bài học"))
                                .thenReturn(GENERATED_SUMMARY);

                /*
                 * DEBUG FLOW:
                 * 1. Dòng 32 videoId=VIDEO_ID.
                 * 2. Dòng 34 metadata mock trả durationSeconds=600 nên tiếp tục.
                 * 3. Gemini trực tiếp ném EXTERNAL_SERVICE_UNAVAILABLE.
                 * 4. Service lấy transcript và gọi lại Gemini bằng văn bản.
                 * 5. Fallback thành công nên response vẫn được trả về.
                 */
                // WHEN: Người dùng yêu cầu tạo summary.
                GenerateSummaryResponse actualResponse =
                                service.generateVideoSummary(WATCH_URL);

                // THEN: Người dùng vẫn nhận summary và transcript được gọi đúng một lần.
                assertThat(actualResponse.summary()).isEqualTo(GENERATED_SUMMARY);
                verify(transcriptService).fetchYoutubeTranscript(VIDEO_ID);
                verify(summaryService).generateSummaryFromTranscript(
                                "vi",
                                "Nội dung bài học");
        }

        @Test
        void generateVideoSummary_propagatesException_whenGeminiGenerationFails() {
                // GIVEN: Metadata và transcript đều hợp lệ.
                when(metadataService.fetchYoutubeVideoMetadata(VIDEO_ID))
                                .thenReturn(new YoutubeVideoMetadata(600));
                when(summaryService.generateSummaryFromYoutubeVideo(WATCH_URL))
                                .thenThrow(new BusinessException(
                                                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                                                "Direct video input is temporarily unavailable"));
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

                /*
                 * DEBUG FLOW:
                 * 1. Dòng 32 parse URL => videoId=VIDEO_ID.
                 * 2. Dòng 34 metadata mock trả 600 giây.
                 * 3. Dòng 36 transcript mock trả language="en", text="Lesson transcript".
                 * 4. Dòng 38-40 gọi summaryService với hai giá trị trên.
                 * 5. Gemini mock ném BusinessException(EXTERNAL_SERVICE_UNAVAILABLE).
                 * 6. Không tạo GenerateSummaryResponse ở dòng 41-47; exception truyền ra
                 * và được catchThrowable gán vào actualException.
                 */
                // WHEN: Service gửi transcript sang Gemini.
                Throwable actualException = catchThrowable(
                                () -> service.generateVideoSummary(WATCH_URL));

                // THEN: Lỗi Gemini phải được truyền nguyên vẹn cho caller.
                assertThat(actualException)
                                .isInstanceOf(BusinessException.class)
                                .extracting(exception -> ((BusinessException) exception).errorCode())
                                .isEqualTo(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        }

        private void assertInvalidYoutubeUrl(Throwable actualException) {
                assertThat(actualException)
                                .isInstanceOf(BusinessException.class)
                                .hasMessage("Enter a valid HTTPS YouTube URL")
                                .extracting(exception -> ((BusinessException) exception).errorCode())
                                .isEqualTo(ErrorCode.INVALID_REQUEST);
        }
}
