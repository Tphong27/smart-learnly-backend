package com.smartlearnly.backend.videoai.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartlearnly.backend.common.exception.GlobalExceptionHandler;
import com.smartlearnly.backend.videoai.dto.VideoAiDtos.GeneratedSummary;
import com.smartlearnly.backend.videoai.dto.VideoAiDtos.GenerateSummaryResponse;
import com.smartlearnly.backend.videoai.service.VideoSummaryService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Kiểm thử contract HTTP của API tạo YouTube summary.
 *
 * <p>
 * Service được mock nên test không gọi YouTube, Python hoặc Gemini thật.
 */
class VideoSummaryControllerTest {

        private static final String VIDEO_ID = "V9i3cGD-mts";
        private static final String YOUTUBE_URL = "https://youtu.be/" + VIDEO_ID;

        private VideoSummaryService service;
        private MockMvc mockMvc;

        @BeforeEach
        void setUp() {
                // GIVEN: Controller dùng service mock để kiểm soát output của API.
                service = mock(VideoSummaryService.class);
                LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
                validator.afterPropertiesSet();

                mockMvc = MockMvcBuilders
                                .standaloneSetup(new VideoSummaryController(service))
                                .setControllerAdvice(new GlobalExceptionHandler())
                                .setValidator(validator)
                                .build();
        }

        /**
         * Mục đích: kiểm tra input/output JSON thực tế của endpoint generate.
         *
         * <p>
         * Input:
         * {@code POST /api/v1/video-summary/generate} với body
         * {@code {"youtubeUrl":"https://youtu.be/V9i3cGD-mts"}}.
         *
         * <p>
         * Expected output: HTTP 200, URL chuẩn, durationSeconds chính xác,
         * durationMinutes tương thích ngược và summary object gồm 3 overview
         * paragraphs cùng danh sách key takeaways.
         */
        @Test
        void handleGenerateVideoSummaryRequest_returnsStructuredApiResponse_whenRequestIsValid()
                        throws Exception {
                // GIVEN: Service tạo thành công summary có cấu trúc.
                GeneratedSummary summary = new GeneratedSummary(
                                List.of(
                                                "Đoạn tổng quan thứ nhất.",
                                                "Đoạn tổng quan thứ hai.",
                                                "Đoạn tổng quan thứ ba."),
                                "Điểm chính",
                                List.of(
                                                "Ý chính thứ nhất",
                                                "Ý chính thứ hai",
                                                "Ý chính thứ ba"));
                GenerateSummaryResponse response = new GenerateSummaryResponse(
                                VIDEO_ID,
                                "https://www.youtube.com/watch?v=" + VIDEO_ID,
                                1_021L,
                                18,
                                summary);
                when(service.generateVideoSummary(YOUTUBE_URL)).thenReturn(response);

                // WHEN: Client gọi API với một URL youtu.be hợp lệ.
                var result = mockMvc.perform(post(
                                "/api/v1/video-summary/generate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {
                                                  "youtubeUrl": "%s"
                                                }
                                                """.formatted(YOUTUBE_URL)));

                // THEN: API trả đúng contract để frontend render paragraph và bullet.
                result.andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.message")
                                                .value("Video summary generated"))
                                .andExpect(jsonPath("$.data.videoId").value(VIDEO_ID))
                                .andExpect(jsonPath("$.data.videoUrl").value(
                                                "https://www.youtube.com/watch?v=" + VIDEO_ID))
                                .andExpect(jsonPath("$.data.durationSeconds").value(1_021))
                                .andExpect(jsonPath("$.data.durationMinutes").value(18))
                                .andExpect(jsonPath(
                                                "$.data.summary.overviewParagraphs.length()")
                                                .value(3))
                                .andExpect(jsonPath(
                                                "$.data.summary.overviewParagraphs[0]")
                                                .value("Đoạn tổng quan thứ nhất."))
                                .andExpect(jsonPath(
                                                "$.data.summary.keyTakeawaysTitle")
                                                .value("Điểm chính"))
                                .andExpect(jsonPath(
                                                "$.data.summary.keyTakeaways.length()")
                                                .value(3))
                                .andExpect(jsonPath(
                                                "$.data.summary.keyTakeaways[0]")
                                                .value("Ý chính thứ nhất"))
                                .andExpect(jsonPath("$.timestamp").exists());

                // THEN: Controller truyền nguyên URL đầu vào xuống service.
                verify(service).generateVideoSummary(YOUTUBE_URL);
        }

        /**
         * Mục đích: kiểm tra DTO validation khi thiếu youtubeUrl.
         *
         * <p>
         * Input: body rỗng {@code {}}.
         * Expected output: HTTP 400 với code VALIDATION_FAILED; service không nhận
         * một request generate hợp lệ.
         */
        @Test
        void handleGenerateVideoSummaryRequest_returnsValidationError_whenYoutubeUrlIsMissing()
                        throws Exception {
                // GIVEN: Request JSON không có field youtubeUrl.
                String requestBody = "{}";

                // WHEN: Client gửi request thiếu tham số bắt buộc.
                var result = mockMvc.perform(post(
                                "/api/v1/video-summary/generate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody));

                // THEN: API trả lỗi validation rõ field và message.
                result.andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.success").value(false))
                                .andExpect(jsonPath("$.code")
                                                .value("VALIDATION_FAILED"))
                                .andExpect(jsonPath("$.errors[0].field")
                                                .value("youtubeUrl"))
                                .andExpect(jsonPath("$.errors[0].message")
                                                .value("YouTube URL is required"));
        }

        /**
         * Mục đích: kiểm tra role được phép gọi endpoint.
         *
         * <p>
         * Input: annotation trên controller.
         * Expected output: có đủ ADMIN, TMO, SME và TRAINER.
         */
        @Test
        void controller_allowsOnlyExpectedRoles() {
                // GIVEN: Controller phải khai báo method security.
                PreAuthorize authorization = VideoSummaryController.class.getAnnotation(
                                PreAuthorize.class);

                // WHEN: Test đọc biểu thức phân quyền.
                String expression = authorization == null
                                ? null
                                : authorization.value();

                // THEN: Bốn role nghiệp vụ đều có trong biểu thức.
                assertThat(expression)
                                .contains("ADMIN", "TMO", "SME", "TRAINER");
        }
}
