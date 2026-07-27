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

        // WHEN + THEN: Duration âm phải bị từ chối.
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

        // WHEN + THEN: Command blank không được dùng để tạo process.
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
            // WHEN + THEN: Backend không trả một transcript rỗng.
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
