package com.smartlearnly.backend.videoai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.videoai.config.VideoAiProperties;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;

/**
 * Unit test cho {@link YoutubeTranscriptService}.
 *
 * <p>Mỗi method {@code @Test} chỉ kiểm tra một input hoặc một lỗi cụ thể để
 * một UTCID trong file Excel có thể ánh xạ trực tiếp tới một test trong code.
 */
class YoutubeTranscriptServiceTest {

    private static final String VIDEO_ID = "dQw4w9WgXcQ";

    @TempDir
    Path tempDirectory;

    /**
     * Mục đích: ghép các transcript segment thành một chuỗi văn bản.
     *
     * <p>Input: language = vi và hai segment "Xin chào", "các bạn".
     * Expected output: language = vi, text = "Xin chào các bạn".
     */
    @Test
    void parseTranscriptWorkerOutput_returnsPlainTranscript_whenWorkerOutputIsValid()
            throws Exception {
        // GIVEN: File JSON do Python worker tạo ra có hai segment hợp lệ.
        Path output = writeJson(
                "valid-transcript.json",
                """
                        {
                          "language": "vi",
                          "segments": [
                            {"start": 0.0, "duration": 2.1, "text": "Xin chào"},
                            {"start": 2.1, "duration": 3.0, "text": "các bạn"}
                          ]
                        }
                        """);

        /*
         * DEBUG FLOW:
         * 1. Dòng 86 readValue tạo worker: language="vi", segments.size=2.
         * 2. Dòng 87-89: worker/language/segments đều hợp lệ => if=false.
         * 3. Vòng for dòng 93 chạy 2 lần; start/duration hữu hạn, text không blank.
         * 4. Lần 1: transcript rỗng nên dòng 100=false; append "Xin chào".
         * 5. Lần 2: transcript không rỗng nên thêm một space rồi append "các bạn".
         * 6. Mỗi lần length<100000; dòng 111 return language="vi",
         *    text="Xin chào các bạn".
         */
        // WHEN: Service parse file output của worker.
        YoutubeTranscriptService.TranscriptResult result =
                service(100_000).parseTranscriptWorkerOutput(output);

        // THEN: Các segment được nối bằng đúng một khoảng trắng.
        assertThat(result.language()).isEqualTo("vi");
        assertThat(result.text()).isEqualTo("Xin chào các bạn");
    }

    /**
     * Mục đích: kiểm tra đúng boundary maxTranscriptCharacters.
     *
     * <p>Input tạo chuỗi "1234 56789" dài đúng 10 ký tự.
     * Expected output: được chấp nhận và language được chuẩn hóa thành en-us.
     */
    @Test
    void parseTranscriptWorkerOutput_acceptsTranscript_whenLengthEqualsCharacterLimit()
            throws Exception {
        // GIVEN: Tổng transcript sau khi ghép dài đúng 10 ký tự.
        Path output = writeJson(
                "boundary-transcript.json",
                """
                        {
                          "language": " EN-us ",
                          "segments": [
                            {"start": 0.0, "duration": 1.0, "text": " 1234 "},
                            {"start": 1.0, "duration": 1.0, "text": "56789 "}
                          ]
                        }
                        """);

        /*
         * DEBUG FLOW:
         * 1. Worker có language=" EN-us " và 2 segment; điều kiện dòng 87=false.
         * 2. Segment 1 strip thành "1234" => transcript length=4.
         * 3. Segment 2 strip thành "56789"; dòng 100 thêm 1 space;
         *    transcript="1234 56789", length=10.
         * 4. Dòng 104 kiểm tra 10>maxCharacters(10)=false nên boundary được nhận.
         * 5. Dòng 112 strip/lower language => "en-us"; dòng 111 return result.
         */
        // WHEN: Service parse transcript với limit bằng 10.
        YoutubeTranscriptService.TranscriptResult result =
                service(10).parseTranscriptWorkerOutput(output);

        // THEN: Boundary bằng giới hạn vẫn hợp lệ.
        assertThat(result.language()).isEqualTo("en-us");
        assertThat(result.text()).isEqualTo("1234 56789");
    }

    /**
     * Mục đích: từ chối transcript vượt giới hạn ký tự.
     *
     * <p>Input: text dài 11 ký tự, maxTranscriptCharacters = 10.
     * Expected output: BUSINESS_RULE_VIOLATION.
     */
    @Test
    void parseTranscriptWorkerOutput_throwsBusinessRuleViolation_whenTranscriptExceedsLimit()
            throws Exception {
        // GIVEN: Một segment chứa 11 ký tự.
        Path output = writeSegment(
                "too-long.json",
                "{\"start\":0,\"duration\":1,\"text\":\"12345678901\"}");

        /*
         * DEBUG FLOW:
         * 1. worker.language="en" (do helper writeSegment), segments.size=1.
         * 2. Vòng for: start=0, duration=1, text="12345678901" đều hợp lệ.
         * 3. Dòng 103 append text => transcript.length=11.
         * 4. maxTranscriptCharacters=10; dòng 104: 11>10=true.
         * 5. Dòng 105 ném BusinessException(BUSINESS_RULE_VIOLATION), không return.
         * 6. assertErrorCode bắt exception từ lambda và kiểm tra errorCode.
         */
        // WHEN + THEN: Transcript vượt limit phải bị từ chối.
        assertErrorCode(
                () -> service(10).parseTranscriptWorkerOutput(output),
                ErrorCode.BUSINESS_RULE_VIOLATION);
    }

    /**
     * Mục đích: từ chối root JSON bằng null.
     *
     * <p>Input file: {@code null}. Expected output:
     * EXTERNAL_SERVICE_UNAVAILABLE.
     */
    @Test
    void parseTranscriptWorkerOutput_throwsUnavailable_whenWorkerResultIsNull()
            throws Exception {
        // GIVEN: Worker ghi literal null thay vì object.
        Path output = writeJson("null-worker.json", "null");

        /*
         * DEBUG FLOW:
         * 1. Dòng 86 readValue(JSON null, WorkerResult.class) => worker=null.
         * 2. Dòng 87 worker==null=true.
         * 3. Toán tử || short-circuit: language và segments không được truy cập,
         *    nên không phát sinh NullPointerException.
         * 4. Dòng 89 ném BusinessException(EXTERNAL_SERVICE_UNAVAILABLE).
         * 5. assertUnavailable bắt lỗi từ lambda và kiểm tra errorCode.
         */
        // WHEN + THEN: Service không có transcript để sử dụng.
        assertUnavailable(() -> service(100).parseTranscriptWorkerOutput(output));
    }

    /**
     * Mục đích: từ chối worker object thiếu toàn bộ field.
     *
     * <p>Input file: {@code {}}. Expected output:
     * EXTERNAL_SERVICE_UNAVAILABLE.
     */
    @Test
    void parseTranscriptWorkerOutput_throwsUnavailable_whenWorkerFieldsAreMissing()
            throws Exception {
        // GIVEN: JSON object không có language và segments.
        Path output = writeJson("empty-worker.json", "{}");

        /*
         * DEBUG FLOW:
         * 1. readValue tạo worker khác null nhưng language=null, segments=null.
         * 2. Dòng 87 worker==null=false; worker.language()==null=true.
         * 3. Do || short-circuit, isBlank và kiểm tra segments không chạy.
         * 4. Dòng 89 ném EXTERNAL_SERVICE_UNAVAILABLE vì thiếu field bắt buộc.
         * 5. assertUnavailable nhận BusinessException, không chạy vòng for.
         */
        // WHEN + THEN: Output thiếu field bắt buộc phải bị từ chối.
        assertUnavailable(() -> service(100).parseTranscriptWorkerOutput(output));
    }

    /**
     * Mục đích: language phải chứa giá trị sử dụng được.
     *
     * <p>Input: language = "   ", segments hợp lệ.
     * Expected output: EXTERNAL_SERVICE_UNAVAILABLE.
     */
    @Test
    void parseTranscriptWorkerOutput_throwsUnavailable_whenLanguageIsBlank()
            throws Exception {
        // GIVEN: Worker không xác định được ngôn ngữ.
        Path output = writeJson(
                "blank-language.json",
                """
                        {
                          "language": "   ",
                          "segments": [
                            {"start":0,"duration":1,"text":"Lesson"}
                          ]
                        }
                        """);

        /*
         * DEBUG FLOW:
         * 1. worker khác null; language="   "; segments có 1 phần tử.
         * 2. Dòng 87: worker null=false, language null=false,
         *    language.isBlank()=true.
         * 3. Điều kiện || dừng tại language blank; segments chưa cần xét.
         * 4. Dòng 89 ném EXTERNAL_SERVICE_UNAVAILABLE; vòng for không chạy.
         */
        // WHEN + THEN: Blank language không tạo được TranscriptResult.
        assertUnavailable(() -> service(100).parseTranscriptWorkerOutput(output));
    }

    /**
     * Mục đích: segments không được bằng null.
     *
     * <p>Input: language = en, segments = null.
     * Expected output: EXTERNAL_SERVICE_UNAVAILABLE.
     */
    @Test
    void parseTranscriptWorkerOutput_throwsUnavailable_whenSegmentsAreNull()
            throws Exception {
        // GIVEN: Worker có language nhưng không có danh sách segment.
        Path output = writeJson(
                "null-segments.json",
                "{\"language\":\"en\",\"segments\":null}");

        /*
         * DEBUG FLOW:
         * 1. worker khác null, language="en" không null/blank.
         * 2. Dòng 88 worker.segments()==null=true.
         * 3. Vế segments.isEmpty() không chạy nhờ short-circuit.
         * 4. Dòng 89 ném EXTERNAL_SERVICE_UNAVAILABLE trước vòng for.
         */
        // WHEN + THEN: Service từ chối output không có segments.
        assertUnavailable(() -> service(100).parseTranscriptWorkerOutput(output));
    }

    /**
     * Mục đích: segments không được là danh sách rỗng.
     *
     * <p>Input: segments = []. Expected output:
     * EXTERNAL_SERVICE_UNAVAILABLE.
     */
    @Test
    void parseTranscriptWorkerOutput_throwsUnavailable_whenSegmentsAreEmpty()
            throws Exception {
        // GIVEN: Worker trả một danh sách không có nội dung.
        Path output = writeJson(
                "empty-segments.json",
                "{\"language\":\"en\",\"segments\":[]}");

        /*
         * DEBUG FLOW:
         * 1. worker/language/segments đều khác null.
         * 2. Dòng 88: segments.isEmpty()=true.
         * 3. Dòng 89 ném EXTERNAL_SERVICE_UNAVAILABLE.
         * 4. Vòng for dòng 93 và return dòng 111 không chạy.
         */
        // WHEN + THEN: Không có segment thì không có transcript.
        assertUnavailable(() -> service(100).parseTranscriptWorkerOutput(output));
    }

    /**
     * Mục đích: mỗi phần tử trong segments phải là object.
     *
     * <p>Input: segments = [null]. Expected output:
     * EXTERNAL_SERVICE_UNAVAILABLE.
     */
    @Test
    void parseTranscriptWorkerOutput_throwsUnavailable_whenSegmentIsNull()
            throws Exception {
        // GIVEN: Danh sách chứa một segment null.
        Path output = writeSegment("null-segment.json", "null");

        /*
         * DEBUG FLOW:
         * 1. Helper tạo worker language="en", segments=[null]; validation worker pass.
         * 2. Vòng for dòng 93 lấy segment=null.
         * 3. Dòng 94 segment==null=true; các vế text/start/duration không chạy.
         * 4. Dòng 97 ném EXTERNAL_SERVICE_UNAVAILABLE.
         * 5. Không append transcript; assertUnavailable bắt BusinessException.
         */
        // WHEN + THEN: Segment null phải bị từ chối.
        assertUnavailable(() -> service(100).parseTranscriptWorkerOutput(output));
    }

    /**
     * Mục đích: text của segment không được bằng null.
     *
     * <p>Input: text = null. Expected output:
     * EXTERNAL_SERVICE_UNAVAILABLE.
     */
    @Test
    void parseTranscriptWorkerOutput_throwsUnavailable_whenSegmentTextIsNull()
            throws Exception {
        // GIVEN: Timestamp hợp lệ nhưng text không tồn tại.
        Path output = writeSegment(
                "null-text.json",
                "{\"start\":0,\"duration\":1,\"text\":null}");

        /*
         * DEBUG FLOW:
         * 1. Segment: start=0, duration=1, text=null.
         * 2. Dòng 94 segment==null=false; segment.text()==null=true.
         * 3. Toán tử || short-circuit nên text.isBlank/start/duration không chạy.
         * 4. Dòng 97 ném EXTERNAL_SERVICE_UNAVAILABLE; không có NPE.
         */
        // WHEN + THEN: Segment không có text phải bị từ chối.
        assertUnavailable(() -> service(100).parseTranscriptWorkerOutput(output));
    }

    /**
     * Mục đích: text chỉ có khoảng trắng không phải transcript.
     *
     * <p>Input: text = "   ". Expected output:
     * EXTERNAL_SERVICE_UNAVAILABLE.
     */
    @Test
    void parseTranscriptWorkerOutput_throwsUnavailable_whenSegmentTextIsBlank()
            throws Exception {
        // GIVEN: Segment không có ký tự nội dung.
        Path output = writeSegment(
                "blank-text.json",
                "{\"start\":0,\"duration\":1,\"text\":\"   \"}");

        /*
         * DEBUG FLOW:
         * 1. Segment khác null; text="   " khác null.
         * 2. Dòng 94 text.isBlank()=true.
         * 3. Kiểm tra finite/start/duration phía sau không chạy do || short-circuit.
         * 4. Dòng 97 ném EXTERNAL_SERVICE_UNAVAILABLE trước text.strip().
         */
        // WHEN + THEN: Blank text phải bị từ chối.
        assertUnavailable(() -> service(100).parseTranscriptWorkerOutput(output));
    }

    /**
     * Mục đích: timestamp bắt đầu không được âm.
     *
     * <p>Input: start = -1. Expected output:
     * EXTERNAL_SERVICE_UNAVAILABLE.
     */
    @Test
    void parseTranscriptWorkerOutput_throwsUnavailable_whenSegmentStartIsNegative()
            throws Exception {
        // GIVEN: Segment bắt đầu trước giây 0.
        Path output = writeSegment(
                "negative-start.json",
                "{\"start\":-1,\"duration\":1,\"text\":\"Lesson\"}");

        /*
         * DEBUG FLOW:
         * 1. Segment: text="Lesson", start=-1, duration=1.
         * 2. Dòng 95: start finite=true, duration finite=true nên hai vế !finite=false.
         * 3. Dòng 96 segment.start()<0 => -1<0=true.
         * 4. Vế duration<=0 không chạy; dòng 97 ném unavailable.
         */
        // WHEN + THEN: Timestamp âm là dữ liệu worker không hợp lệ.
        assertUnavailable(() -> service(100).parseTranscriptWorkerOutput(output));
    }

    /**
     * Mục đích: duration phải lớn hơn 0.
     *
     * <p>Input: duration = 0. Expected output:
     * EXTERNAL_SERVICE_UNAVAILABLE.
     */
    @Test
    void parseTranscriptWorkerOutput_throwsUnavailable_whenSegmentDurationIsZero()
            throws Exception {
        // GIVEN: Segment có duration bằng 0.
        Path output = writeSegment(
                "zero-duration.json",
                "{\"start\":0,\"duration\":0,\"text\":\"Lesson\"}");

        /*
         * DEBUG FLOW:
         * 1. start=0 và duration=0 đều là số hữu hạn.
         * 2. Dòng 96: start<0=false; duration<=0 => 0<=0=true.
         * 3. Dòng 97 ném EXTERNAL_SERVICE_UNAVAILABLE.
         * 4. text không được append và TranscriptResult không được tạo.
         */
        // WHEN + THEN: Segment không có thời lượng phải bị từ chối.
        assertUnavailable(() -> service(100).parseTranscriptWorkerOutput(output));
    }

    /**
     * Mục đích: duration âm là dữ liệu không hợp lệ.
     *
     * <p>Input: duration = -1. Expected output:
     * EXTERNAL_SERVICE_UNAVAILABLE.
     */
    @Test
    void parseTranscriptWorkerOutput_throwsUnavailable_whenSegmentDurationIsNegative()
            throws Exception {
        // GIVEN: Segment có duration âm.
        Path output = writeSegment(
                "negative-duration.json",
                "{\"start\":0,\"duration\":-1,\"text\":\"Lesson\"}");

        /*
         * DEBUG FLOW:
         * 1. start=0, duration=-1, cả hai vẫn finite.
         * 2. Dòng 96: start<0=false; duration<=0 => -1<=0=true.
         * 3. Dòng 97 ném unavailable; dòng 99-114 không chạy.
         */
        // WHEN + THEN: Duration âm phải bị từ chối.
        assertUnavailable(() -> service(100).parseTranscriptWorkerOutput(output));
    }

    /**
     * CODE UNDER TEST: YoutubeTranscriptService.java dòng 93-98, kiểm tra
     * !Double.isFinite(segment.start()).
     * Biến vào: start = "NaN", duration = 1, text = "Lesson".
     * Mục tiêu: đi qua nhánh start không phải một số hữu hạn.
     */
    @Test
    void parseTranscriptWorkerOutput_throwsUnavailable_whenSegmentStartIsNaN()
            throws Exception {
        // GIVEN - variables: Jackson chuyển chuỗi "NaN" thành Double.NaN.
        Path output = writeSegment(
                "nan-start.json",
                "{\"start\":\"NaN\",\"duration\":1,\"text\":\"Lesson\"}");

        /*
         * DEBUG FLOW:
         * 1. Jackson chuyển start="NaN" thành Double.NaN; duration=1.
         * 2. Dòng 95: Double.isFinite(NaN)=false nên !isFinite(start)=true.
         * 3. Các vế duration/start âm không chạy do || short-circuit.
         * 4. Dòng 97 ném EXTERNAL_SERVICE_UNAVAILABLE.
         */
        // WHEN - code line: vòng for kiểm tra Double.isFinite(start).
        // THEN - expected: segment không hữu hạn trả EXTERNAL_SERVICE_UNAVAILABLE.
        assertUnavailable(() -> service(100).parseTranscriptWorkerOutput(output));
    }

    /**
     * CODE UNDER TEST: YoutubeTranscriptService.java dòng 93-98, kiểm tra
     * !Double.isFinite(segment.duration()).
     * Biến vào: start = 0, duration = "Infinity", text = "Lesson".
     * Mục tiêu: đi qua nhánh duration không phải một số hữu hạn.
     */
    @Test
    void parseTranscriptWorkerOutput_throwsUnavailable_whenSegmentDurationIsInfinite()
            throws Exception {
        // GIVEN - variables: Jackson chuyển "Infinity" thành Double.POSITIVE_INFINITY.
        Path output = writeSegment(
                "infinite-duration.json",
                "{\"start\":0,\"duration\":\"Infinity\",\"text\":\"Lesson\"}");

        /*
         * DEBUG FLOW:
         * 1. Jackson tạo start=0 và duration=Double.POSITIVE_INFINITY.
         * 2. Dòng 95: start finite=true nên vế start=false;
         *    duration finite=false nên !isFinite(duration)=true.
         * 3. Dòng 96 không cần xét; dòng 97 ném unavailable.
         */
        // WHEN - code line: vòng for kiểm tra Double.isFinite(duration).
        // THEN - expected: duration vô hạn trả EXTERNAL_SERVICE_UNAVAILABLE.
        assertUnavailable(() -> service(100).parseTranscriptWorkerOutput(output));
    }

    /**
     * Mục đích: không chạy worker khi tính năng video AI bị tắt.
     *
     * <p>Input: enabled = false, video ID hợp lệ.
     * Expected output: EXTERNAL_SERVICE_UNAVAILABLE.
     */
    @Test
    void fetchYoutubeTranscript_throwsUnavailable_whenVideoAiIsDisabled()
            throws Exception {
        // GIVEN: Script tồn tại nhưng feature bị tắt.
        VideoAiProperties properties =
                runtimeProperties(createWorkerFile());
        properties.setEnabled(false);

        /*
         * DEBUG FLOW:
         * 1. Dòng 33 gọi validateRuntime(VIDEO_ID).
         * 2. Dòng 118: !properties.isEnabled()=!false=true.
         * 3. Dòng 119 ném EXTERNAL_SERVICE_UNAVAILABLE trước khối try dòng 37.
         * 4. videoId/script/command không được kiểm tra; không tạo process/file tạm.
         * 5. assertUnavailable bắt BusinessException từ lambda.
         */
        // WHEN + THEN: Service dừng trước khi tạo process.
        assertUnavailable(() ->
                new YoutubeTranscriptService(properties).fetchYoutubeTranscript(VIDEO_ID));
    }

    /**
     * Mục đích: video ID là tham số bắt buộc.
     *
     * <p>Input: videoId = null. Expected output: INVALID_REQUEST.
     */
    @Test
    void fetchYoutubeTranscript_throwsInvalidRequest_whenVideoIdIsNull()
            throws Exception {
        // GIVEN: Runtime hợp lệ nhưng không có video ID.
        VideoAiProperties properties =
                runtimeProperties(createWorkerFile());

        /*
         * DEBUG FLOW:
         * 1. enabled=true nên dòng 118=false.
         * 2. Tham số videoId=null; dòng 121 videoId==null=true.
         * 3. Vế videoId.matches(...) không chạy do || short-circuit.
         * 4. Dòng 122 ném BusinessException(INVALID_REQUEST).
         * 5. Không kiểm tra script và không vào try/process; assertErrorCode bắt lỗi.
         */
        // WHEN + THEN: null không được truyền xuống Python.
        assertErrorCode(
                () -> new YoutubeTranscriptService(properties).fetchYoutubeTranscript(null),
                ErrorCode.INVALID_REQUEST);
    }

    /**
     * Mục đích: video ID phải đúng pattern 11 ký tự.
     *
     * <p>Input: videoId = "short". Expected output: INVALID_REQUEST.
     */
    @Test
    void fetchYoutubeTranscript_throwsInvalidRequest_whenVideoIdFormatIsInvalid()
            throws Exception {
        // GIVEN: Video ID chỉ có 5 ký tự.
        VideoAiProperties properties =
                runtimeProperties(createWorkerFile());

        /*
         * DEBUG FLOW:
         * 1. videoId="short" khác null.
         * 2. Dòng 121: "short".matches("[A-Za-z0-9_-]{11}")=false.
         * 3. Điều kiện if=true; dòng 122 ném INVALID_REQUEST.
         * 4. Script hợp lệ vẫn không được dùng và ProcessBuilder không được tạo.
         */
        // WHEN + THEN: ID sai format không được gọi worker.
        assertErrorCode(
                () -> new YoutubeTranscriptService(properties)
                        .fetchYoutubeTranscript("short"),
                ErrorCode.INVALID_REQUEST);
    }

    /**
     * Mục đích: script Python phải tồn tại trên filesystem.
     *
     * <p>Input: transcriptScriptPath trỏ tới file không tồn tại.
     * Expected output: EXTERNAL_SERVICE_UNAVAILABLE.
     */
    @Test
    void fetchYoutubeTranscript_throwsUnavailable_whenScriptFileDoesNotExist() {
        // GIVEN: Đường dẫn worker chưa được tạo.
        VideoAiProperties properties = runtimeProperties(
                tempDirectory.resolve("missing.py"));

        /*
         * DEBUG FLOW:
         * 1. enabled=true, VIDEO_ID đúng regex nên qua dòng 118-123.
         * 2. Dòng 124 script=.../missing.py, khác null.
         * 3. Dòng 125 Files.isRegularFile(script)=false vì file không tồn tại.
         * 4. Toán tử || dừng; Files.isReadable không cần gọi.
         * 5. Dòng 126 ném unavailable trước khi tạo file/process.
         */
        // WHEN + THEN: Service không thể khởi chạy script bị thiếu.
        assertUnavailable(() ->
                new YoutubeTranscriptService(properties).fetchYoutubeTranscript(VIDEO_ID));
    }

    /**
     * Mục đích: transcriptScriptPath không được bằng null.
     *
     * <p>Input: transcriptScriptPath = null.
     * Expected output: EXTERNAL_SERVICE_UNAVAILABLE.
     */
    @Test
    void fetchYoutubeTranscript_throwsUnavailable_whenScriptPathIsNull()
            throws Exception {
        // GIVEN: Cấu hình không có đường dẫn worker.
        VideoAiProperties properties =
                runtimeProperties(createWorkerFile());
        properties.setTranscriptScriptPath(null);

        /*
         * DEBUG FLOW:
         * 1. enabled và VIDEO_ID hợp lệ.
         * 2. Dòng 124 script=null.
         * 3. Dòng 125 script==null=true; hai lời gọi Files phía sau không chạy.
         * 4. Dòng 126 ném EXTERNAL_SERVICE_UNAVAILABLE; không có NPE.
         */
        // WHEN + THEN: Service báo runtime transcript chưa sẵn sàng.
        assertUnavailable(() ->
                new YoutubeTranscriptService(properties).fetchYoutubeTranscript(VIDEO_ID));
    }

    /**
     * Mục đích: Python command không được blank.
     *
     * <p>Input: pythonCommand = " ". Expected output:
     * EXTERNAL_SERVICE_UNAVAILABLE.
     */
    @Test
    void fetchYoutubeTranscript_throwsUnavailable_whenPythonCommandIsBlank()
            throws Exception {
        // GIVEN: Python command không có ký tự thực.
        VideoAiProperties properties =
                runtimeProperties(createWorkerFile());
        properties.setPythonCommand(" ");

        /*
         * DEBUG FLOW:
         * 1. validateRuntime pass vì enabled, ID và script hợp lệ.
         * 2. Dòng 38-39 tạo output/log file; dòng 41 gọi requiredArgument(" ").
         * 3. Dòng 156 normalized=" ".trim()="".
         * 4. Dòng 157 normalized.isEmpty()=true; dòng 161 ném unavailable.
         * 5. Catch IOException không chạy vì đây là BusinessException.
         * 6. finally dòng 78-80 xóa hai file tạm; không start process.
         */
        // WHEN + THEN: Command blank không được dùng để tạo process.
        assertUnavailable(() ->
                new YoutubeTranscriptService(properties).fetchYoutubeTranscript(VIDEO_ID));
    }

    /**
     * CODE UNDER TEST: YoutubeTranscriptService.java dòng 155-163, biểu thức
     * value == null ? "" : value.trim() trong requiredArgument().
     * Biến vào: pythonCommand = null.
     * Mục tiêu: đi qua vế true của toán tử ba ngôi rồi nhánh normalized.isEmpty().
     */
    @Test
    void fetchYoutubeTranscript_throwsUnavailable_whenPythonCommandIsNull()
            throws Exception {
        // GIVEN - variable: script tồn tại nhưng pythonCommand không được cấu hình.
        VideoAiProperties properties =
                runtimeProperties(createWorkerFile());
        properties.setPythonCommand(null);

        /*
         * DEBUG FLOW:
         * 1. validateRuntime pass và hai file tạm được tạo.
         * 2. Dòng 156: value==null=true => normalized="".
         * 3. Dòng 157 normalized.isEmpty()=true; dòng 161 ném unavailable.
         * 4. ProcessBuilder chưa được tạo; catch IOException không bắt lỗi này.
         * 5. finally vẫn xóa output/processLog; assertUnavailable kiểm tra code.
         */
        // WHEN - code line: requiredArgument(null, "Python command").
        // THEN - expected: null được chuẩn hóa thành rỗng và trả unavailable.
        assertUnavailable(() ->
                new YoutubeTranscriptService(properties).fetchYoutubeTranscript(VIDEO_ID));
    }

    /**
     * Mục đích: giới hạn độ dài Python command.
     *
     * <p>Input: command dài 257 ký tự. Expected output:
     * EXTERNAL_SERVICE_UNAVAILABLE.
     */
    @Test
    void fetchYoutubeTranscript_throwsUnavailable_whenPythonCommandIsTooLong()
            throws Exception {
        // GIVEN: Command vượt maximum 256 ký tự.
        VideoAiProperties properties =
                runtimeProperties(createWorkerFile());
        properties.setPythonCommand("x".repeat(257));

        /*
         * DEBUG FLOW:
         * 1. requiredArgument nhận chuỗi 257 ký tự; trim không đổi độ dài.
         * 2. Dòng 157: isEmpty=false; normalized.length()>256 => 257>256=true.
         * 3. Các kiểm tra ký tự điều khiển phía sau không chạy.
         * 4. Dòng 161 ném unavailable; finally dọn file tạm.
         */
        // WHEN + THEN: Command quá dài phải bị từ chối.
        assertUnavailable(() ->
                new YoutubeTranscriptService(properties).fetchYoutubeTranscript(VIDEO_ID));
    }

    /**
     * Mục đích: từ chối null byte trong Python command.
     *
     * <p>Input: {@code python\0bad}. Expected output:
     * EXTERNAL_SERVICE_UNAVAILABLE.
     */
    @Test
    void fetchYoutubeTranscript_throwsUnavailable_whenPythonCommandContainsNullByte()
            throws Exception {
        // GIVEN: Command chứa ký tự điều khiển null.
        VideoAiProperties properties =
                runtimeProperties(createWorkerFile());
        properties.setPythonCommand("python\0bad");

        /*
         * DEBUG FLOW:
         * 1. normalized="python\0bad": không rỗng và length<=256.
         * 2. Dòng 158 normalized.indexOf('\0')>=0=true.
         * 3. Kiểm tra CR/LF không chạy; dòng 161 ném unavailable.
         * 4. Không start process; finally xóa output/log file.
         */
        // WHEN + THEN: Command nguy hiểm không được chạy.
        assertUnavailable(() ->
                new YoutubeTranscriptService(properties).fetchYoutubeTranscript(VIDEO_ID));
    }

    /**
     * Mục đích: từ chối carriage return trong Python command.
     *
     * <p>Input: {@code python\rbad}. Expected output:
     * EXTERNAL_SERVICE_UNAVAILABLE.
     */
    @Test
    void fetchYoutubeTranscript_throwsUnavailable_whenPythonCommandContainsCarriageReturn()
            throws Exception {
        // GIVEN: Command chứa ký tự xuống dòng CR.
        VideoAiProperties properties =
                runtimeProperties(createWorkerFile());
        properties.setPythonCommand("python\rbad");

        /*
         * DEBUG FLOW:
         * 1. Command không rỗng, không quá dài và không chứa null byte.
         * 2. Dòng 159 normalized.contains("\r")=true.
         * 3. Vế kiểm tra LF không chạy; dòng 161 ném unavailable.
         * 4. finally dọn file tạm; worker không được gọi.
         */
        // WHEN + THEN: Command nhiều dòng không được chạy.
        assertUnavailable(() ->
                new YoutubeTranscriptService(properties).fetchYoutubeTranscript(VIDEO_ID));
    }

    /**
     * Mục đích: từ chối line feed trong Python command.
     *
     * <p>Input: {@code python\nbad}. Expected output:
     * EXTERNAL_SERVICE_UNAVAILABLE.
     */
    @Test
    void fetchYoutubeTranscript_throwsUnavailable_whenPythonCommandContainsLineFeed()
            throws Exception {
        // GIVEN: Command chứa ký tự xuống dòng LF.
        VideoAiProperties properties =
                runtimeProperties(createWorkerFile());
        properties.setPythonCommand("python\nbad");

        /*
         * DEBUG FLOW:
         * 1. Các vế empty/length/null-byte/CR tại dòng 157-159 đều false.
         * 2. Dòng 160 normalized.contains("\n")=true.
         * 3. Dòng 161 ném EXTERNAL_SERVICE_UNAVAILABLE.
         * 4. ProcessBuilder không start; finally xóa hai file tạm.
         */
        // WHEN + THEN: Command nhiều dòng không được chạy.
        assertUnavailable(() ->
                new YoutubeTranscriptService(properties).fetchYoutubeTranscript(VIDEO_ID));
    }

    /**
     * Mục đích: kiểm tra toàn bộ câu lệnh gọi Python và timeout tối đa.
     *
     * <p>Input: transcriptTimeout = 10 phút, worker trả "Xin chào".
     * Expected output: command có --video-id và timeout bị giới hạn còn 5 phút.
     */
    @Test
    void fetchYoutubeTranscript_returnsTranscript_andCapsTimeoutAtFiveMinutes()
            throws Exception {
        // GIVEN: Worker hợp lệ và timeout cấu hình lớn hơn maximum.
        Path worker = createWorkerFile();
        VideoAiProperties properties = runtimeProperties(worker);
        properties.setTranscriptTimeout(Duration.ofMinutes(10));
        Process process = completedProcess(0);
        AtomicReference<String[]> command = new AtomicReference<>();

        // GIVEN: Process mock tạo file JSON giống output của Python thật.
        try (MockedConstruction<ProcessBuilder> ignored = processBuilder(
                process,
                command,
                null,
                """
                        {
                          "language": "vi",
                          "segments": [
                            {"start":0,"duration":1,"text":"Xin chào"}
                          ]
                        }
                        """)) {
            /*
             * DEBUG FLOW:
             * 1. validateRuntime pass; dòng 38-39 tạo output/log file tạm.
             * 2. Dòng 40-47 tạo command: python-test, worker path, --video-id,
             *    VIDEO_ID, --output, output path; mock lưu mảng vào command.
             * 3. builder.start() mock ghi JSON và trả process có exitCode=0.
             * 4. normalizedTimeout: configured=10 phút; dòng 152 so với 5 phút
             *    cho kết quả >0 nên timeout thực=5 phút=300000 ms.
             * 5. process.waitFor(...)=true; exitValue!=0=false; output regular/size>0.
             * 6. Dòng 68 parse JSON => language="vi", text="Xin chào".
             * 7. finally xóa output/log; method return TranscriptResult.
             */
            // WHEN: Service lấy transcript cho video ID.
            YoutubeTranscriptService.TranscriptResult result =
                    new YoutubeTranscriptService(properties).fetchYoutubeTranscript(VIDEO_ID);

            // THEN: Kết quả và từng argument truyền cho worker phải chính xác.
            assertThat(result.language()).isEqualTo("vi");
            assertThat(result.text()).isEqualTo("Xin chào");
            assertThat(command.get()).containsSequence(
                    "python-test",
                    worker.toAbsolutePath().normalize().toString(),
                    "--video-id",
                    VIDEO_ID,
                    "--output");
            verify(process).waitFor(
                    300_000L,
                    TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Mục đích: timeout bằng 0 sử dụng giá trị mặc định 60 giây.
     *
     * <p>Input: transcriptTimeout = Duration.ZERO.
     * Expected output: Process.waitFor(60000 ms).
     */
    @Test
    void fetchYoutubeTranscript_usesDefaultTimeout_whenConfiguredTimeoutIsZero()
            throws Exception {
        // GIVEN: Timeout bằng 0 và worker trả transcript hợp lệ.
        Path worker = createWorkerFile();
        VideoAiProperties properties = runtimeProperties(worker);
        properties.setTranscriptTimeout(Duration.ZERO);
        Process process = completedProcess(0);

        try (MockedConstruction<ProcessBuilder> ignored =
                     successfulProcess(process)) {
            /*
             * DEBUG FLOW:
             * 1. Dòng 148 timeout=Duration.ZERO.
             * 2. Dòng 149: timeout==null=false; isNegative=false; isZero=true.
             * 3. Dòng 150 return Duration.ofSeconds(60).
             * 4. Dòng 53 gọi process.waitFor(60000, MILLISECONDS); mock=true.
             * 5. exitCode=0, output JSON hợp lệ; parse/return rồi finally dọn file.
             */
            // WHEN: Service chạy worker.
            new YoutubeTranscriptService(properties).fetchYoutubeTranscript(VIDEO_ID);

            // THEN: Timeout mặc định là 60 giây.
            verify(process).waitFor(
                    60_000L,
                    TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Mục đích: timeout âm sử dụng giá trị mặc định 60 giây.
     *
     * <p>Input: transcriptTimeout = -1 giây.
     * Expected output: Process.waitFor(60000 ms).
     */
    @Test
    void fetchYoutubeTranscript_usesDefaultTimeout_whenConfiguredTimeoutIsNegative()
            throws Exception {
        // GIVEN: Timeout âm và worker trả transcript hợp lệ.
        Path worker = createWorkerFile();
        VideoAiProperties properties = runtimeProperties(worker);
        properties.setTranscriptTimeout(Duration.ofSeconds(-1));
        Process process = completedProcess(0);

        try (MockedConstruction<ProcessBuilder> ignored =
                     successfulProcess(process)) {
            /*
             * DEBUG FLOW:
             * 1. Dòng 148 timeout=-1 giây.
             * 2. Dòng 149: null=false; timeout.isNegative()=true;
             *    vế isZero không cần xét.
             * 3. Dòng 150 trả timeout mặc định 60 giây.
             * 4. waitFor nhận 60000 ms; process exit 0 và transcript được parse.
             */
            // WHEN: Service chạy worker.
            new YoutubeTranscriptService(properties).fetchYoutubeTranscript(VIDEO_ID);

            // THEN: Timeout mặc định là 60 giây.
            verify(process).waitFor(
                    60_000L,
                    TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Mục đích: timeout null sử dụng giá trị mặc định 60 giây.
     *
     * <p>Input: transcriptTimeout = null.
     * Expected output: Process.waitFor(60000 ms).
     */
    @Test
    void fetchYoutubeTranscript_usesDefaultTimeout_whenConfiguredTimeoutIsNull()
            throws Exception {
        // GIVEN: Không có timeout trong cấu hình.
        Path worker = createWorkerFile();
        VideoAiProperties properties = runtimeProperties(worker);
        properties.setTranscriptTimeout(null);
        Process process = completedProcess(0);

        try (MockedConstruction<ProcessBuilder> ignored =
                     successfulProcess(process)) {
            /*
             * DEBUG FLOW:
             * 1. Dòng 148 timeout=null.
             * 2. Dòng 149 timeout==null=true; isNegative/isZero không được gọi,
             *    tránh NullPointerException.
             * 3. Dòng 150 trả Duration.ofSeconds(60).
             * 4. waitFor nhận 60000 ms; worker success; finally xóa file tạm.
             */
            // WHEN: Service chạy worker.
            new YoutubeTranscriptService(properties).fetchYoutubeTranscript(VIDEO_ID);

            // THEN: Timeout mặc định là 60 giây.
            verify(process).waitFor(
                    60_000L,
                    TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Mục đích: ánh xạ marker TRANSCRIPT_DISABLED.
     *
     * <p>Input process log: TRANSCRIPT_DISABLED.
     * Expected output: BUSINESS_RULE_VIOLATION.
     */
    @Test
    void fetchYoutubeTranscript_throwsBusinessRuleViolation_whenTranscriptIsDisabled()
            throws Exception {
        // GIVEN: Python báo phụ đề bị tắt.
        Path worker = createWorkerFile();

        /*
         * DEBUG FLOW:
         * 1. assertWorkerError tạo process: waitFor=true, exitValue=1 và log
         *    processLog="TRANSCRIPT_DISABLED".
         * 2. Dòng 53 timeout=false; dòng 59 exitValue()!=0 => 1!=0=true.
         * 3. Dòng 60 đọc log; dòng 131 value="TRANSCRIPT_DISABLED".
         * 4. Dòng 132: contains TRANSCRIPT_DISABLED=true; dòng 133 return
         *    BusinessException(BUSINESS_RULE_VIOLATION).
         * 5. Dòng 63 throw lỗi đó; finally xóa file; helper kiểm tra errorCode.
         */
        // WHEN + THEN: Đây là business rule của video, không phải lỗi hệ thống.
        assertWorkerError(
                worker,
                "TRANSCRIPT_DISABLED",
                ErrorCode.BUSINESS_RULE_VIOLATION);
    }

    /**
     * Mục đích: ánh xạ marker TRANSCRIPT_NOT_FOUND.
     *
     * <p>Input process log: TRANSCRIPT_NOT_FOUND.
     * Expected output: BUSINESS_RULE_VIOLATION.
     */
    @Test
    void fetchYoutubeTranscript_throwsBusinessRuleViolation_whenTranscriptIsNotFound()
            throws Exception {
        // GIVEN: Python không tìm thấy transcript dùng được.
        Path worker = createWorkerFile();

        /*
         * DEBUG FLOW:
         * 1. Process mock exitValue=1; log="TRANSCRIPT_NOT_FOUND".
         * 2. Dòng 59 true; readBoundedLog trả marker trên.
         * 3. Dòng 132: contains DISABLED=false, contains NOT_FOUND=true;
         *    nhóm điều kiện OR=true.
         * 4. Dòng 133 tạo BUSINESS_RULE_VIOLATION; dòng 63 ném ra.
         * 5. finally dọn file và assertWorkerError kiểm tra code.
         */
        // WHEN + THEN: Video không đủ điều kiện tạo summary.
        assertWorkerError(
                worker,
                "TRANSCRIPT_NOT_FOUND",
                ErrorCode.BUSINESS_RULE_VIOLATION);
    }

    /**
     * Mục đích: ánh xạ marker VIDEO_UNAVAILABLE.
     *
     * <p>Input process log: VIDEO_UNAVAILABLE.
     * Expected output: RESOURCE_NOT_FOUND.
     */
    @Test
    void fetchYoutubeTranscript_throwsResourceNotFound_whenVideoIsUnavailable()
            throws Exception {
        // GIVEN: Python cho biết video không tồn tại hoặc không truy cập được.
        Path worker = createWorkerFile();

        /*
         * DEBUG FLOW:
         * 1. Process exit 1; processLog="VIDEO_UNAVAILABLE".
         * 2. Dòng 132 không chứa hai transcript marker => false.
         * 3. Dòng 138 contains("VIDEO_UNAVAILABLE")=true.
         * 4. Dòng 139 return BusinessException(RESOURCE_NOT_FOUND);
         *    dòng 63 ném lỗi này, finally xóa file.
         */
        // WHEN + THEN: API trả lỗi không tìm thấy tài nguyên.
        assertWorkerError(
                worker,
                "VIDEO_UNAVAILABLE",
                ErrorCode.RESOURCE_NOT_FOUND);
    }

    /**
     * Mục đích: ánh xạ marker YOUTUBE_BLOCKED.
     *
     * <p>Input process log: YOUTUBE_BLOCKED.
     * Expected output: EXTERNAL_SERVICE_UNAVAILABLE.
     */
    @Test
    void fetchYoutubeTranscript_throwsUnavailable_whenYoutubeBlocksTranscriptRequest()
            throws Exception {
        // GIVEN: YouTube tạm thời chặn worker.
        Path worker = createWorkerFile();

        /*
         * DEBUG FLOW:
         * 1. Process exit 1; log được uppercase thành "YOUTUBE_BLOCKED".
         * 2. Dòng 132=false; dòng 138=false.
         * 3. Dòng 141 contains("YOUTUBE_BLOCKED")=true.
         * 4. Dòng 142 return EXTERNAL_SERVICE_UNAVAILABLE với message blocked.
         * 5. Dòng 63 throw; finally dọn file; helper kiểm tra code.
         */
        // WHEN + THEN: Đây là lỗi dịch vụ ngoài tạm thời.
        assertWorkerError(
                worker,
                "YOUTUBE_BLOCKED",
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
    }

    /**
     * Mục đích: xử lý process log không thuộc marker đã biết.
     *
     * <p>Input log: "unexpected failure".
     * Expected output: EXTERNAL_SERVICE_UNAVAILABLE.
     */
    @Test
    void fetchYoutubeTranscript_throwsUnavailable_whenWorkerReturnsUnknownFailure()
            throws Exception {
        // GIVEN: Worker exit code 1 với lỗi không xác định.
        Path worker = createWorkerFile();

        /*
         * DEBUG FLOW:
         * 1. Process exit 1; value="UNEXPECTED FAILURE" sau toUpperCase.
         * 2. Các if marker tại dòng 132, 138 và 141 đều false.
         * 3. Dòng 144 chạy nhánh mặc định, return unavailable
         *    "Unable to get the YouTube transcript".
         * 4. Dòng 63 throw; finally xóa file; assertWorkerError kiểm tra code.
         */
        // WHEN + THEN: Lỗi được che thành lỗi dịch vụ ngoài thống nhất.
        assertWorkerError(
                worker,
                "unexpected failure",
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
    }

    /**
     * Mục đích: dừng process khi chờ transcript quá thời gian.
     *
     * <p>Input: Process.waitFor trả false.
     * Expected output: EXTERNAL_SERVICE_UNAVAILABLE và destroyForcibly được gọi.
     */
    @Test
    void fetchYoutubeTranscript_throwsUnavailable_andStopsProcess_whenWorkerTimesOut()
            throws Exception {
        // GIVEN: Process không kết thúc trong thời gian cho phép.
        Path worker = createWorkerFile();
        Process process = mock(Process.class);
        when(process.waitFor(anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(false);
        when(process.destroyForcibly()).thenReturn(process);

        try (MockedConstruction<ProcessBuilder> ignored = processBuilder(
                process,
                new AtomicReference<>(),
                null,
                null)) {
            /*
             * DEBUG FLOW:
             * 1. ProcessBuilder.start() mock trả process; normalized timeout=2 giây.
             * 2. Dòng 53 process.waitFor(...)=false => điều kiện timeout=true.
             * 3. Dòng 54 gọi destroyForcibly(); dòng 55 chờ thêm 5 giây.
             * 4. Dòng 56 ném BusinessException(EXTERNAL_SERVICE_UNAVAILABLE).
             * 5. Không đọc exitValue/output; finally xóa output/log file.
             */
            // WHEN + THEN: Timeout được ánh xạ thành unavailable.
            assertUnavailable(() ->
                    new YoutubeTranscriptService(runtimeProperties(worker))
                            .fetchYoutubeTranscript(VIDEO_ID));

            // THEN: Process treo phải bị dừng cưỡng chế.
            verify(process).destroyForcibly();
        }
    }

    /**
     * Mục đích: giữ trạng thái interrupted của thread.
     *
     * <p>Input: Process.waitFor ném InterruptedException.
     * Expected output: EXTERNAL_SERVICE_UNAVAILABLE, process bị dừng và
     * Thread.currentThread().isInterrupted() = true.
     */
    @Test
    void fetchYoutubeTranscript_throwsUnavailable_andRestoresInterrupt_whenWaitIsInterrupted()
            throws Exception {
        // GIVEN: Thread bị interrupt trong lúc đợi worker.
        Path worker = createWorkerFile();
        Process process = mock(Process.class);
        when(process.waitFor(anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenThrow(new InterruptedException("test interrupt"));
        when(process.destroyForcibly()).thenReturn(process);

        try (MockedConstruction<ProcessBuilder> ignored = processBuilder(
                process,
                new AtomicReference<>(),
                null,
                null)) {
            try {
                /*
                 * DEBUG FLOW:
                 * 1. Dòng 53 gọi process.waitFor và mock ném InterruptedException.
                 * 2. Luồng nhảy thẳng vào catch dòng 69; không kiểm tra exit/output.
                 * 3. Dòng 70 gọi Thread.currentThread().interrupt() => flag=true.
                 * 4. process đã được gán nên dòng 71 process!=null=true;
                 *    dòng 72 destroyForcibly().
                 * 5. Dòng 74 ném unavailable; finally service xóa file tạm.
                 * 6. finally của test gọi Thread.interrupted() để xóa flag sau assertion.
                 */
                // WHEN + THEN: Service báo unavailable.
                assertUnavailable(() ->
                        new YoutubeTranscriptService(runtimeProperties(worker))
                                .fetchYoutubeTranscript(VIDEO_ID));

                // THEN: Interrupt flag được khôi phục và process bị dừng.
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
                verify(process).destroyForcibly();
            } finally {
                Thread.interrupted();
            }
        }
    }

    /**
     * Mục đích: process exit 0 vẫn phải tạo file output.
     *
     * <p>Input: exitCode = 0 nhưng không có JSON output.
     * Expected output: EXTERNAL_SERVICE_UNAVAILABLE.
     */
    @Test
    void fetchYoutubeTranscript_throwsUnavailable_whenWorkerCreatesNoOutput()
            throws Exception {
        // GIVEN: Worker báo thành công nhưng không ghi transcript.
        Path worker = createWorkerFile();
        Process process = completedProcess(0);

        try (MockedConstruction<ProcessBuilder> ignored = processBuilder(
                process,
                new AtomicReference<>(),
                null,
                null)) {
            /*
             * DEBUG FLOW:
             * 1. output temp file được tạo ở dòng 38 nhưng mock worker không ghi JSON.
             * 2. waitFor=true và exitValue=0 nên dòng 53/59 đều không throw.
             * 3. Dòng 65: Files.isRegularFile(output)=true nên vế đầu=false;
             *    Files.size(output)==0 => true.
             * 4. Dòng 66 ném unavailable "YouTube returned no transcript".
             * 5. finally xóa output/log; parseTranscriptWorkerOutput không chạy.
             */
            // WHEN + THEN: Backend không trả một transcript rỗng.
            assertUnavailable(() ->
                    new YoutubeTranscriptService(runtimeProperties(worker))
                            .fetchYoutubeTranscript(VIDEO_ID));
        }
    }

    /**
     * CODE UNDER TEST: YoutubeTranscriptService.java dòng 65-67, nhánh
     * !Files.isRegularFile(output) trong khối try.
     * Biến vào: exitCode = 0 nhưng worker xóa file output trước khi kết thúc.
     * Mục tiêu: phân biệt file không tồn tại với test file tồn tại nhưng rỗng.
     */
    @Test
    void fetchYoutubeTranscript_throwsUnavailable_whenWorkerDeletesOutputFile()
            throws Exception {
        // GIVEN - variables: script hợp lệ, process exitCode = 0.
        Path worker = createWorkerFile();
        Process process = completedProcess(0);

        try (MockedConstruction<ProcessBuilder> ignored = mockConstruction(
                ProcessBuilder.class,
                (builder, context) -> {
                    // MOCK - lưu cấu hình redirect giống ProcessBuilder thật.
                    when(builder.redirectErrorStream(true))
                            .thenReturn(builder);
                    when(builder.redirectOutput(any(File.class)))
                            .thenReturn(builder);

                    // MOCK - arguments[5] là biến output truyền sau option "--output".
                    String[] arguments =
                            (String[]) context.arguments().get(0);
                    when(builder.start()).thenAnswer(invocation -> {
                        Files.deleteIfExists(Path.of(arguments[5]));
                        return process;
                    });
                })) {
            /*
             * DEBUG FLOW:
             * 1. Service tạo output temp ở dòng 38; builder.start mock xóa đúng
             *    Path arguments[5] rồi trả process exitCode=0.
             * 2. waitFor=true; exitValue!=0=false.
             * 3. Dòng 65 Files.isRegularFile(output)=false vì file đã bị xóa;
             *    vế Files.size không chạy do || short-circuit.
             * 4. Dòng 66 ném unavailable; finally deleteIfExists an toàn.
             */
            // WHEN - code line: process thành công rồi kiểm tra Files.isRegularFile(output).
            // THEN - expected: file đã bị xóa trả EXTERNAL_SERVICE_UNAVAILABLE.
            assertUnavailable(() ->
                    new YoutubeTranscriptService(runtimeProperties(worker))
                            .fetchYoutubeTranscript(VIDEO_ID));
        }
    }

    /**
     * Mục đích: xử lý lỗi không thể start process Python.
     *
     * <p>Input: ProcessBuilder.start() ném IOException.
     * Expected output: EXTERNAL_SERVICE_UNAVAILABLE.
     */
    @Test
    void fetchYoutubeTranscript_throwsUnavailable_whenWorkerCannotStart()
            throws Exception {
        // GIVEN: Script tồn tại nhưng hệ điều hành không start được process.
        Path worker = createWorkerFile();
        try (MockedConstruction<ProcessBuilder> ignored = mockConstruction(
                ProcessBuilder.class,
                (builder, context) -> {
                    when(builder.redirectErrorStream(true))
                            .thenReturn(builder);
                    when(builder.redirectOutput(any(File.class)))
                            .thenReturn(builder);
                    when(builder.start())
                            .thenThrow(new IOException("cannot start"));
                })) {
            /*
             * DEBUG FLOW:
             * 1. validateRuntime pass; output/log file được tạo; command hợp lệ.
             * 2. Dòng 50 builder.start() ném IOException("cannot start");
             *    biến process vẫn bằng null.
             * 3. Luồng nhảy vào catch IOException dòng 75.
             * 4. Dòng 77 ném BusinessException(EXTERNAL_SERVICE_UNAVAILABLE).
             * 5. finally dòng 78-80 xóa hai file tạm dù process chưa start.
             */
            // WHEN + THEN: IOException được ánh xạ thành unavailable.
            assertUnavailable(() ->
                    new YoutubeTranscriptService(runtimeProperties(worker))
                            .fetchYoutubeTranscript(VIDEO_ID));
        }
    }

    /**
     * Mục đích: xử lý file output không phải JSON hợp lệ.
     *
     * <p>Input worker output: {@code {not-json}.
     * Expected output: EXTERNAL_SERVICE_UNAVAILABLE.
     */
    @Test
    void fetchYoutubeTranscript_throwsUnavailable_whenWorkerOutputIsMalformedJson()
            throws Exception {
        // GIVEN: Worker exit 0 nhưng ghi JSON lỗi.
        Path worker = createWorkerFile();
        Process process = completedProcess(0);
        try (MockedConstruction<ProcessBuilder> ignored = processBuilder(
                process,
                new AtomicReference<>(),
                null,
                "{not-json")) {
            /*
             * DEBUG FLOW:
             * 1. Mock worker ghi output="{not-json", waitFor=true, exitCode=0.
             * 2. Dòng 65 file regular và size>0 nên tiếp tục dòng 68.
             * 3. parseTranscriptWorkerOutput dòng 86 readValue ném JsonParseException,
             *    là subclass của IOException.
             * 4. Exception quay về fetch và bị catch IOException dòng 75.
             * 5. Dòng 77 ném unavailable; finally xóa output/log.
             */
            // WHEN + THEN: JSON parser error không thoát trực tiếp ra API.
            assertUnavailable(() ->
                    new YoutubeTranscriptService(runtimeProperties(worker))
                            .fetchYoutubeTranscript(VIDEO_ID));
        }
    }

    private YoutubeTranscriptService service(int maxCharacters) {
        VideoAiProperties properties = new VideoAiProperties();
        properties.setMaxTranscriptCharacters(maxCharacters);
        return new YoutubeTranscriptService(properties);
    }

    private Path writeJson(String fileName, String json)
            throws IOException {
        Path output = tempDirectory.resolve(fileName);
        Files.writeString(output, json);
        return output;
    }

    private Path writeSegment(String fileName, String segment)
            throws IOException {
        return writeJson(
                fileName,
                """
                        {
                          "language": "en",
                          "segments": [%s]
                        }
                        """.formatted(segment));
    }

    private Path createWorkerFile() throws IOException {
        Path worker = tempDirectory.resolve("worker.py");
        Files.writeString(worker, "# test worker");
        return worker;
    }

    private VideoAiProperties runtimeProperties(Path worker) {
        VideoAiProperties properties = new VideoAiProperties();
        properties.setEnabled(true);
        properties.setPythonCommand("python-test");
        properties.setTranscriptScriptPath(worker);
        properties.setTranscriptTimeout(Duration.ofSeconds(2));
        return properties;
    }

    private Process completedProcess(int exitCode)
            throws InterruptedException {
        Process process = mock(Process.class);
        when(process.waitFor(anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(true);
        when(process.exitValue()).thenReturn(exitCode);
        return process;
    }

    private MockedConstruction<ProcessBuilder> successfulProcess(
            Process process) {
        return processBuilder(
                process,
                new AtomicReference<>(),
                null,
                """
                        {
                          "language": "en",
                          "segments": [
                            {"start":0,"duration":1,"text":"Lesson"}
                          ]
                        }
                        """);
    }

    private MockedConstruction<ProcessBuilder> processBuilder(
            Process process,
            AtomicReference<String[]> command,
            String processLog,
            String workerOutput) {
        AtomicReference<File> logFile = new AtomicReference<>();
        return mockConstruction(
                ProcessBuilder.class,
                (builder, context) -> {
                    String[] arguments =
                            (String[]) context.arguments().get(0);
                    command.set(arguments);
                    when(builder.redirectErrorStream(true))
                            .thenReturn(builder);
                    when(builder.redirectOutput(any(File.class)))
                            .thenAnswer(invocation -> {
                                logFile.set(invocation.getArgument(0));
                                return builder;
                            });
                    when(builder.start()).thenAnswer(invocation -> {
                        if (processLog != null) {
                            Files.writeString(
                                    logFile.get().toPath(),
                                    processLog);
                        }
                        if (workerOutput != null) {
                            Files.writeString(
                                    Path.of(arguments[5]),
                                    workerOutput);
                        }
                        return process;
                    });
                });
    }

    private void assertWorkerError(
            Path worker,
            String processLog,
            ErrorCode expected) throws Exception {
        Process process = completedProcess(1);
        try (MockedConstruction<ProcessBuilder> ignored = processBuilder(
                process,
                new AtomicReference<>(),
                processLog,
                null)) {
            assertErrorCode(
                    () -> new YoutubeTranscriptService(
                            runtimeProperties(worker)).fetchYoutubeTranscript(VIDEO_ID),
                    expected);
        }
    }

    private void assertUnavailable(ThrowingOperation operation) {
        assertErrorCode(
                operation,
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
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

    @FunctionalInterface
    private interface ThrowingOperation {

        void run() throws Exception;
    }
}
