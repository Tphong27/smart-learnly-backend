package com.smartlearnly.backend.hls.controller;
import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.hls.service.HlsProcessingCallbackService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@Hidden @RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/internal/hls/jobs")
public class HlsProcessingCallbackController {
    private static final int MAX_BODY = 16 * 1024;
    private final HlsProcessingCallbackService service;
    @PostMapping(value = "/callback", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> callback(
            @RequestHeader(value = "X-HLS-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-HLS-Signature", required = false) String signature,
            HttpServletRequest request) {
        try {
            byte[] body = request.getInputStream().readNBytes(MAX_BODY + 1);
            if (body.length > MAX_BODY)
                throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE, "HLS callback body is too large");
            service.accept(body, timestamp, signature);
            return ResponseEntity.ok(ApiResponse.success("HLS callback accepted"));
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "HLS callback body could not be read");
        }
    }
}
