package com.smartlearnly.backend.payment.sepay;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class DefaultSePayTransactionClient implements SePayTransactionClient {
    private final SePayProperties sePayProperties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public DefaultSePayTransactionClient(SePayProperties sePayProperties) {
        this(sePayProperties, RestClient.builder());
    }

    DefaultSePayTransactionClient(SePayProperties sePayProperties, RestClient.Builder restClientBuilder) {
        this.sePayProperties = sePayProperties;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public List<SePayTransactionCandidate> findTransactions(SePayTransactionQuery query) {
        validateConfiguration();
        try {
            String response = restClient.get()
                    .uri(buildUri(query))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + sePayProperties.getApiToken())
                    .retrieve()
                    .body(String.class);
            return parseTransactions(response);
        }
        catch (RestClientException | IOException | IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "SePay transaction service is unavailable"
            );
        }
    }

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

    private List<SePayTransactionCandidate> parseTransactions(String response) throws IOException {
        JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
        JsonNode transactions = root.isArray() ? root : root.get("transactions");
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

    private void validateConfiguration() {
        if (sePayProperties.getApiToken() == null || sePayProperties.getApiToken().isBlank()) {
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "SePay transaction service is not configured"
            );
        }
    }

    private String normalizeBaseUrl() {
        String baseUrl = sePayProperties.getApiBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = SePayProperties.DEFAULT_API_BASE_URL;
        }
        return baseUrl.replaceAll("/+$", "");
    }

    private void addQueryParam(UriComponentsBuilder builder, String name, String value) {
        if (value != null && !value.isBlank()) {
            builder.queryParam(name, value.trim());
        }
    }

    private String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private String text(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

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
