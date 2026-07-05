package com.smartlearnly.backend.hls.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.hls.dto.HlsProcessingCallbackRequest;
import jakarta.validation.Validator;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
@Service @RequiredArgsConstructor
public class HlsProcessingCallbackService {
    private final HlsCallbackSignatureVerifier verifier;
    private final HlsProcessingStateService stateService;
    private final Validator validator;
    private final ObjectMapper mapper = new ObjectMapper();
    public void accept(byte[] body, String timestamp, String signature) {
        verifier.verify(body, timestamp, signature);
        HlsProcessingCallbackRequest request;
        try { request = mapper.readValue(body, HlsProcessingCallbackRequest.class); }
        catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "HLS callback body is invalid");
        }
        var violations = validator.validate(request);
        if (!violations.isEmpty())
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "HLS callback validation failed");
        stateService.applyCallback(request);
    }
}
