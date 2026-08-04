package com.smartlearnly.backend.payment.sepay.service;

import com.smartlearnly.backend.payment.sepay.config.SePayProperties;
import com.smartlearnly.backend.payment.sepay.dto.SePayTransactionCandidate;
import com.smartlearnly.backend.payment.sepay.dto.SePayTransactionQuery;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class DefaultSePayTransactionClient implements SePayTransactionClient {
    private static final Logger log = LoggerFactory.getLogger(DefaultSePayTransactionClient.class);

    private final SePayProperties sePayProperties;
    private final SystemSettingsService systemSettingsService;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    // Tạo HTTP client mặc định dùng khi Spring khởi tạo tích hợp SePay.
    public DefaultSePayTransactionClient(SePayProperties sePayProperties, SystemSettingsService systemSettingsService) {
        this(sePayProperties, systemSettingsService, RestClient.builder());
    }

    // Cho phép test truyền RestClient builder có mock server mà không đổi logic production.
    DefaultSePayTransactionClient(SePayProperties sePayProperties, SystemSettingsService systemSettingsService, RestClient.Builder restClientBuilder) {
        this.sePayProperties = sePayProperties;
        this.systemSettingsService = systemSettingsService;
        this.restClient = restClientBuilder.build();
    }

    @Override
    // Gọi API SePay để tìm giao dịch phù hợp và chuyển lỗi ngoài thành lỗi nghiệp vụ an toàn.
    public List<SePayTransactionCandidate> findTransactions(SePayTransactionQuery query) {
        validateConfiguration();
        try {
            String response = restClient.get()
                    .uri(buildUri(query))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + systemSettingsService.resolveSePayRuntimeSettings().apiToken())
                    .retrieve()
                    .body(String.class);
            return parseTransactions(response);
        }
        catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            log.warn("SePay transaction API returned HTTP {}", status);
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "SePay transaction service returned HTTP " + status
            );
        }
        catch (RestClientException | IOException | IllegalArgumentException exception) {
            log.warn(
                    "SePay transaction API call failed errorType={} causeType={}",
                    exception.getClass().getSimpleName(),
                    exception.getCause() == null ? "none" : exception.getCause().getClass().getSimpleName()
            );
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "SePay transaction service is unavailable"
            );
        }
    }

    // Tạo URI API v2 với đúng bộ lọc mã, chiều giao dịch và khoảng số tiền.
    private URI buildUri(SePayTransactionQuery query) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(normalizeBaseUrl())
                .path("/v2/transactions");
        addQueryParam(builder, "q", query.q());
        addQueryParam(builder, "transfer_type", query.transferType());
        addQueryParam(builder, "amount_in_min", decimal(query.amountInMin()));
        addQueryParam(builder, "amount_in_max", decimal(query.amountInMax()));
        if (query.perPage() > 0) {
            builder.queryParam("per_page", query.perPage());
        }
        addQueryParam(builder, "timestamp_format", query.timestampFormat());
        return builder.encode(StandardCharsets.UTF_8).build().toUri();
    }

    // Đọc response API v2 và vẫn hỗ trợ envelope v1 của proxy cũ.
    private List<SePayTransactionCandidate> parseTransactions(String response) throws IOException {
        JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
        JsonNode transactions = root.isArray() ? root : root.get("data");
        if (transactions == null || !transactions.isArray()) {
            // Giữ khả năng đọc envelope v1 tại các môi trường còn dùng proxy cũ.
            transactions = root.get("transactions");
        }
        if (transactions == null || !transactions.isArray()) {
            return List.of();
        }

        List<SePayTransactionCandidate> candidates = new ArrayList<>();
        for (JsonNode transaction : transactions) {
            candidates.add(new SePayTransactionCandidate(
                    text(transaction, "id"),
                    text(transaction, "transaction_date"),
                    text(transaction, "account_number"),
                    text(transaction, "transfer_type"),
                    decimal(transaction, "amount_in"),
                    text(transaction, "transaction_content"),
                    text(transaction, "reference_number"),
                    text(transaction, "code")
            ));
        }
        return candidates;
    }

    // Chặn gọi API khi token runtime chưa được cấu hình.
    private void validateConfiguration() {
        if (!systemSettingsService.resolveSePayRuntimeSettings().hasApiToken()) {
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "SePay transaction service is not configured"
            );
        }
    }

    // Chuẩn hóa base URL và loại dấu gạch chéo cuối để ghép endpoint ổn định.
    private String normalizeBaseUrl() {
        String baseUrl = sePayProperties.getApiBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = SePayProperties.DEFAULT_API_BASE_URL;
        }
        return baseUrl.replaceAll("/+$", "");
    }

    // Chỉ thêm query parameter khi giá trị thực sự có nội dung.
    private void addQueryParam(UriComponentsBuilder builder, String name, String value) {
        if (value != null && !value.isBlank()) {
            builder.queryParam(name, value.trim());
        }
    }

    // Chuyển số tiền sang chuỗi thập phân không có số 0 dư cho query API.
    private String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    // Đọc trường chữ tùy chọn từ một giao dịch trong JSON response.
    private String text(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    // Đọc số tiền từ cả kiểu number và chuỗi, trả null nếu dữ liệu không hợp lệ.
    private BigDecimal decimal(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.decimalValue();
        }
        if (value.isTextual()) {
            try {
                return new BigDecimal(value.asText());
            }
            catch (NumberFormatException exception) {
                return null;
            }
        }
        return null;
    }
}
