package com.smartlearnly.backend.payment.sepay.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.smartlearnly.backend.payment.sepay.dto.SePayWebhookAck;
import com.smartlearnly.backend.payment.sepay.service.SePayWebhookService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SePayWebhookControllerTest {
    @Mock
    private SePayWebhookService sePayWebhookService;

    @Test
    void receiveShouldDelegateRawBodyAndReturnSePaySuccessAck() {
        SePayWebhookController controller = new SePayWebhookController(sePayWebhookService);
        byte[] rawBody = "{\"id\":92704}".getBytes(StandardCharsets.UTF_8);

        SePayWebhookAck response = controller.receive(rawBody, "sha256=abc", "1781863200");

        assertThat(response.success()).isTrue();
        verify(sePayWebhookService).receive(rawBody, "sha256=abc", "1781863200");
    }
}
