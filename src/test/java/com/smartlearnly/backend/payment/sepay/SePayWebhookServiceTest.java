package com.smartlearnly.backend.payment.sepay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SePayWebhookServiceTest {
    @Mock
    private SePayWebhookSignatureVerifier signatureVerifier;
    @Mock
    private SePayWebhookEventRepository webhookEventRepository;
    @Mock
    private SePayPaymentMatchingService paymentMatchingService;

    private SePayWebhookService service;

    @BeforeEach
    void setUp() {
        service = new SePayWebhookService(
                signatureVerifier,
                webhookEventRepository,
                paymentMatchingService
        );
    }

    @Test
    void receiveShouldStoreVerifiedRawEventAndProcessPaymentMatch() {
        String payload = "{\"id\":92704,\"code\":\"SLPABC123DEF456\",\"transferAmount\":5000000}";
        byte[] rawBody = payload.getBytes(StandardCharsets.UTF_8);
        when(signatureVerifier.verify(rawBody, "sha256=valid", "1781863200")).thenReturn(1781863200L);
        when(webhookEventRepository.saveReceivedEvent(92704L, "sha256=valid", 1781863200L, payload))
                .thenReturn(true);

        service.receive(rawBody, "sha256=valid", "1781863200");

        verify(webhookEventRepository).saveReceivedEvent(92704L, "sha256=valid", 1781863200L, payload);
        ArgumentCaptor<SePayWebhookPayload> payloadCaptor = ArgumentCaptor.forClass(SePayWebhookPayload.class);
        verify(paymentMatchingService).process(payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().id()).isEqualTo(92704L);
        assertThat(payloadCaptor.getValue().code()).isEqualTo("SLPABC123DEF456");
    }

    @Test
    void receiveShouldSkipDuplicateAlreadyProcessedEvent() {
        String payload = "{\"id\":92704,\"transferAmount\":5000000}";
        byte[] rawBody = payload.getBytes(StandardCharsets.UTF_8);
        when(signatureVerifier.verify(rawBody, "sha256=valid", "1781863200")).thenReturn(1781863200L);
        when(webhookEventRepository.saveReceivedEvent(92704L, "sha256=valid", 1781863200L, payload))
                .thenReturn(false);
        when(webhookEventRepository.findProcessingStatusByGatewayEventIdForUpdate(92704L))
                .thenReturn(Optional.of("PROCESSED"));

        service.receive(rawBody, "sha256=valid", "1781863200");

        verify(webhookEventRepository).saveReceivedEvent(92704L, "sha256=valid", 1781863200L, payload);
        verify(paymentMatchingService, never()).process(any());
    }

    @Test
    void receiveShouldReprocessDuplicateReceivedOrFailedEvent() {
        for (String status : java.util.List.of("RECEIVED", "FAILED")) {
            String payload = "{\"id\":92704,\"transferAmount\":5000000}";
            byte[] rawBody = payload.getBytes(StandardCharsets.UTF_8);
            org.mockito.Mockito.reset(signatureVerifier, webhookEventRepository, paymentMatchingService);
            when(signatureVerifier.verify(rawBody, "sha256=valid", "1781863200")).thenReturn(1781863200L);
            when(webhookEventRepository.saveReceivedEvent(92704L, "sha256=valid", 1781863200L, payload))
                    .thenReturn(false);
            when(webhookEventRepository.findProcessingStatusByGatewayEventIdForUpdate(92704L))
                    .thenReturn(Optional.of(status));

            service.receive(rawBody, "sha256=valid", "1781863200");

            verify(paymentMatchingService).process(any(SePayWebhookPayload.class));
        }
    }

    @Test
    void receiveShouldRejectInvalidJsonAfterSignatureVerification() {
        byte[] rawBody = "{\"id\":".getBytes(StandardCharsets.UTF_8);
        when(signatureVerifier.verify(rawBody, "sha256=valid", "1781863200")).thenReturn(1781863200L);

        assertThatThrownBy(() -> service.receive(rawBody, "sha256=valid", "1781863200"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        verify(webhookEventRepository, never()).saveReceivedEvent(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString()
        );
        verify(paymentMatchingService, never()).process(any());
    }

    @Test
    void receiveShouldRejectMissingNumericEventIdAfterSignatureVerification() {
        byte[] rawBody = "{\"id\":\"92704\"}".getBytes(StandardCharsets.UTF_8);
        when(signatureVerifier.verify(rawBody, "sha256=valid", "1781863200")).thenReturn(1781863200L);

        assertThatThrownBy(() -> service.receive(rawBody, "sha256=valid", "1781863200"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        verify(webhookEventRepository, never()).saveReceivedEvent(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString()
        );
        verify(paymentMatchingService, never()).process(any());
    }
}
