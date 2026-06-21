package com.smartlearnly.backend.payment.sepay;

import com.smartlearnly.backend.commerce.repository.SePayOrderRepository;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultSePayPaymentInstructionService implements SePayPaymentInstructionService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_PAYMENT_CODE_ATTEMPTS = 5;
    private static final int PAYMENT_CODE_SUFFIX_LENGTH = 12;
    private static final String DEFAULT_PAYMENT_CODE_PREFIX = "SLP";

    private final SePayProperties sePayProperties;
    private final SePayOrderRepository sePayOrderRepository;

    @Override
    public SePayPaymentInstruction createInstruction(SePayPaymentInstructionRequest request) {
        if (request == null || request.amount() == null || request.amount().signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "SePay payment amount must be greater than 0");
        }

        String accountNumber = requireDisplayConfig(sePayProperties.getAccountNumber());
        String bankName = requireDisplayConfig(sePayProperties.getBankName());
        String accountName = requireDisplayConfig(sePayProperties.getAccountName());
        String paymentCode = generateUniquePaymentCode();

        return new SePayPaymentInstruction(
                paymentCode,
                accountNumber,
                bankName,
                accountName,
                buildQrUrl(request, paymentCode, accountNumber, bankName, accountName),
                request.amount(),
                request.expiresAt()
        );
    }

    private String requireDisplayConfig(String value) {
        if (isBlank(value)) {
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "SePay payment display configuration is incomplete"
            );
        }
        return value.trim();
    }

    private String generateUniquePaymentCode() {
        for (int attempt = 0; attempt < MAX_PAYMENT_CODE_ATTEMPTS; attempt++) {
            String paymentCode = paymentCodePrefix() + randomBase36(PAYMENT_CODE_SUFFIX_LENGTH);
            if (!sePayOrderRepository.existsByPaymentCode(paymentCode)) {
                return paymentCode;
            }
        }
        throw new BusinessException(
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                "Unable to generate a unique SePay payment code"
        );
    }

    private String paymentCodePrefix() {
        if (isBlank(sePayProperties.getPaymentCodePrefix())) {
            return DEFAULT_PAYMENT_CODE_PREFIX;
        }
        return sePayProperties.getPaymentCodePrefix().trim().toUpperCase(Locale.ROOT);
    }

    private String randomBase36(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            builder.append(Character.forDigit(RANDOM.nextInt(36), 36));
        }
        return builder.toString().toUpperCase(Locale.ROOT);
    }

    private String buildQrUrl(
            SePayPaymentInstructionRequest request,
            String paymentCode,
            String accountNumber,
            String bankName,
            String accountName
    ) {
        String template = isBlank(sePayProperties.getQrUrlTemplate())
                ? SePayProperties.DEFAULT_QR_URL_TEMPLATE
                : sePayProperties.getQrUrlTemplate();
        Map<String, String> placeholders = Map.of(
                "paymentCode", paymentCode,
                "accountNumber", accountNumber,
                "bankName", bankName,
                "accountName", accountName,
                "amount", formatVndAmount(request.amount()),
                "orderCode", request.orderCode() == null ? "" : request.orderCode()
        );

        String qrUrl = template;
        for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
            qrUrl = qrUrl.replace(
                    "{" + placeholder.getKey() + "}",
                    encodePlaceholderValue(placeholder.getValue())
            );
        }
        return qrUrl;
    }

    private String encodePlaceholderValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private String formatVndAmount(BigDecimal amount) {
        BigDecimal normalized = amount.stripTrailingZeros();
        if (normalized.scale() > 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "SePay payment amount must be a whole VND value");
        }
        return normalized.toPlainString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
