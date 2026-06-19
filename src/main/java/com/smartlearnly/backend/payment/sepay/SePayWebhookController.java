package com.smartlearnly.backend.payment.sepay;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments/webhooks")
public class SePayWebhookController {
    private final SePayWebhookService sePayWebhookService;

    @PostMapping(path = "/sepay", consumes = MediaType.APPLICATION_JSON_VALUE)
    public SePayWebhookAck receive(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "X-SePay-Signature", required = false) String signature,
            @RequestHeader(value = "X-SePay-Timestamp", required = false) String timestamp
    ) {
        sePayWebhookService.receive(rawBody, signature, timestamp);
        return SePayWebhookAck.accepted();
    }
}
