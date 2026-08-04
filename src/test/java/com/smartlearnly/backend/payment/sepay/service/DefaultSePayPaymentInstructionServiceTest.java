package com.smartlearnly.backend.payment.sepay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.admin.settings.service.SystemSettingsService;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.SePayBankDisplaySettings;
import com.smartlearnly.backend.commerce.repository.SePayOrderRepository;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.payment.sepay.config.SePayProperties;
import com.smartlearnly.backend.payment.sepay.dto.SePayPaymentInstruction;
import com.smartlearnly.backend.payment.sepay.dto.SePayPaymentInstructionRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultSePayPaymentInstructionServiceTest {
    @Mock
    private SePayOrderRepository sePayOrderRepository;

    @Mock
    private SystemSettingsService systemSettingsService;

    private SePayProperties sePayProperties;
    private DefaultSePayPaymentInstructionService service;

    @BeforeEach
    void setUp() {
        sePayProperties = new SePayProperties();
        sePayProperties.setAccountNumber("123 456/789");
        sePayProperties.setBankName("MB Bank");
        sePayProperties.setAccountName("Smart Learnly");
        sePayProperties.setPaymentCodePrefix("SLP");
        sePayProperties.setQrUrlTemplate(
                "https://qr.example/pay?code={paymentCode}"
                        + "&account={accountNumber}"
                        + "&bank={bankName}"
                        + "&name={accountName}"
                        + "&amount={amount}"
                        + "&order={orderCode}"
        );
        lenient().when(systemSettingsService.resolveSePayBankDisplaySettings())
                .thenReturn(new SePayBankDisplaySettings("123 456/789", "MB Bank", "Smart Learnly"));
        service = new DefaultSePayPaymentInstructionService(sePayProperties, sePayOrderRepository, systemSettingsService);
    }

    @Test
    void createInstructionShouldReturnBankDetailsPaymentCodeAndEncodedQrUrl() {
        when(sePayOrderRepository.existsByPaymentCode(anyString())).thenReturn(false);

        SePayPaymentInstruction instruction = service.createInstruction(request(new BigDecimal("399000")));

        assertThat(instruction.paymentCode()).startsWith("SLP").matches("SLP[0-9A-Z]{12}");
        assertThat(instruction.transferContent()).isEqualTo(instruction.paymentCode());
        assertThat(instruction.bankAccountNumber()).isEqualTo("123 456/789");
        assertThat(instruction.bankName()).isEqualTo("MB Bank");
        assertThat(instruction.accountName()).isEqualTo("Smart Learnly");
        assertThat(instruction.amount()).isEqualByComparingTo("399000");
        assertThat(instruction.expiresAt()).isEqualTo(Instant.parse("2026-06-19T10:30:00Z"));
        assertThat(instruction.qrUrl())
                .contains("code=" + instruction.paymentCode())
                .contains("account=123%20456%2F789")
                .contains("bank=MB%20Bank")
                .contains("name=Smart%20Learnly")
                .contains("amount=399000")
                .contains("order=SLP-ORDER-20260619%2F001");
        verify(sePayOrderRepository).existsByPaymentCode(instruction.paymentCode());
    }

    @Test
    void createInstructionShouldPrefixVietinBankTransferContentWithSevqr() {
        sePayProperties.setQrUrlTemplate(
                "https://qr.example/pay?content={transferContent}&code={paymentCode}"
        );
        when(systemSettingsService.resolveSePayBankDisplaySettings())
                .thenReturn(new SePayBankDisplaySettings("123456789", "VietinBank", "Smart Learnly"));
        when(sePayOrderRepository.existsByPaymentCode(anyString())).thenReturn(false);

        SePayPaymentInstruction instruction = service.createInstruction(request(new BigDecimal("10000")));

        assertThat(instruction.transferContent()).isEqualTo("SEVQR " + instruction.paymentCode());
        assertThat(instruction.qrUrl())
                .contains("content=SEVQR%20" + instruction.paymentCode())
                .contains("code=" + instruction.paymentCode());
    }

    @Test
    void createInstructionShouldSupportConfiguredTransferContentTemplate() {
        sePayProperties.setTransferContentTemplate("ORDER {paymentCode}");
        sePayProperties.setQrUrlTemplate("https://qr.example/pay?content={transferContent}");
        when(sePayOrderRepository.existsByPaymentCode(anyString())).thenReturn(false);

        SePayPaymentInstruction instruction = service.createInstruction(request(new BigDecimal("10000")));

        assertThat(instruction.transferContent()).isEqualTo("ORDER " + instruction.paymentCode());
        assertThat(instruction.qrUrl()).contains("content=ORDER%20" + instruction.paymentCode());
    }

    @Test
    void createInstructionShouldRejectTransferContentTemplateWithoutPaymentCode() {
        sePayProperties.setTransferContentTemplate("SEVQR");
        when(sePayOrderRepository.existsByPaymentCode(anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.createInstruction(request(new BigDecimal("10000"))))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE));
    }

    @Test
    void createInstructionShouldUseSystemSettingsOverStaticProperties() {
        sePayProperties.setAccountNumber("env-account");
        sePayProperties.setBankName("Env Bank");
        sePayProperties.setAccountName("Env Account");
        when(systemSettingsService.resolveSePayBankDisplaySettings())
                .thenReturn(new SePayBankDisplaySettings("db-account", "DB Bank", "DB Account"));
        when(sePayOrderRepository.existsByPaymentCode(anyString())).thenReturn(false);

        SePayPaymentInstruction instruction = service.createInstruction(request(new BigDecimal("1000")));

        assertThat(instruction.bankAccountNumber()).isEqualTo("db-account");
        assertThat(instruction.bankName()).isEqualTo("DB Bank");
        assertThat(instruction.accountName()).isEqualTo("DB Account");
    }

    @Test
    void createInstructionShouldRejectNullZeroAndNegativeAmounts() {
        assertInvalidAmount(null);
        assertInvalidAmount(BigDecimal.ZERO);
        assertInvalidAmount(new BigDecimal("-1"));
        verifyNoInteractions(sePayOrderRepository);
    }

    @Test
    void createInstructionShouldRejectMissingDisplayConfigurationWithoutExposingSecrets() {
        sePayProperties.setWebhookSecret("webhook-secret-test-value");
        sePayProperties.setApiToken("api-token-test-value");
        when(systemSettingsService.resolveSePayBankDisplaySettings())
                .thenReturn(new SePayBankDisplaySettings("123456789", "MB Bank", " "));

        assertThatThrownBy(() -> service.createInstruction(request(new BigDecimal("1000"))))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
                    assertThat(exception.getMessage())
                            .doesNotContain("webhook-secret-test-value")
                            .doesNotContain("api-token-test-value");
                });
        verifyNoInteractions(sePayOrderRepository);
    }

    @Test
    void createInstructionShouldRetryWhenPaymentCodeCollides() {
        when(sePayOrderRepository.existsByPaymentCode(anyString())).thenReturn(true, false);

        SePayPaymentInstruction instruction = service.createInstruction(request(new BigDecimal("1000")));

        assertThat(instruction.paymentCode()).startsWith("SLP").matches("SLP[0-9A-Z]{12}");
        verify(sePayOrderRepository, times(2)).existsByPaymentCode(anyString());
    }

    @Test
    void createInstructionShouldFailWhenPaymentCodeCollidesTooManyTimes() {
        sePayProperties.setWebhookSecret("webhook-secret-test-value");
        sePayProperties.setApiToken("api-token-test-value");
        when(sePayOrderRepository.existsByPaymentCode(anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.createInstruction(request(new BigDecimal("1000"))))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
                    assertThat(exception.getMessage())
                            .doesNotContain("webhook-secret-test-value")
                            .doesNotContain("api-token-test-value");
                });
        verify(sePayOrderRepository, times(5)).existsByPaymentCode(anyString());
    }

    private void assertInvalidAmount(BigDecimal amount) {
        assertThatThrownBy(() -> service.createInstruction(request(amount)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    private SePayPaymentInstructionRequest request(BigDecimal amount) {
        return new SePayPaymentInstructionRequest(
                UUID.randomUUID(),
                "SLP-ORDER-20260619/001",
                UUID.randomUUID(),
                amount,
                "VND",
                Instant.parse("2026-06-19T10:30:00Z")
        );
    }
}
