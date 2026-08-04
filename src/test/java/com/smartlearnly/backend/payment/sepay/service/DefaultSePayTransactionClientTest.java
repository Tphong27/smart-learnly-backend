package com.smartlearnly.backend.payment.sepay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.smartlearnly.backend.admin.settings.service.SystemSettingsService;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.SePayRuntimeSettings;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.payment.sepay.config.SePayProperties;
import com.smartlearnly.backend.payment.sepay.dto.SePayTransactionCandidate;
import com.smartlearnly.backend.payment.sepay.dto.SePayTransactionQuery;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DefaultSePayTransactionClientTest {
    @Test
    void findTransactionsShouldCallDocumentedEndpointAndMapFields() {
        SePayProperties sePayProperties = new SePayProperties();
        sePayProperties.setApiToken("fake-api-token");
        sePayProperties.setApiBaseUrl("https://sepay.example.test/");
        SystemSettingsService settingsService = mock(SystemSettingsService.class);
        when(settingsService.resolveSePayRuntimeSettings()).thenReturn(new SePayRuntimeSettings("fake-api-token", "fake-webhook-secret"));
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        DefaultSePayTransactionClient client = new DefaultSePayTransactionClient(sePayProperties, settingsService, restClientBuilder);
        server.expect(requestTo("https://sepay.example.test/v2/transactions"
                        + "?q=SLPABC123DEF456"
                        + "&transfer_type=in"
                        + "&amount_in_min=399000"
                        + "&amount_in_max=399000"
                        + "&per_page=20"
                        + "&timestamp_format=iso8601"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer fake-api-token"))
                .andRespond(withSuccess("""
                        {
                          "status": "success",
                          "data": [
                            {
                              "id": "0f171a36-5a4e-4e00-b7fb-c8a4560d9c10",
                              "transaction_date": "2026-06-19T17:30:00+07:00",
                              "account_number": "123456789",
                              "transfer_type": "in",
                              "amount_in": "399000",
                              "transaction_content": "Thanh toan SLPABC123DEF456",
                              "reference_number": "FT24012345678",
                              "code": "SLPABC123DEF456"
                            }
                          ],
                          "meta": {
                            "pagination": {
                              "total": 1,
                              "per_page": 20,
                              "current_page": 1,
                              "last_page": 1,
                              "has_more": false
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        List<SePayTransactionCandidate> transactions = client.findTransactions(
                SePayTransactionQuery.forPaymentCode("SLPABC123DEF456", new BigDecimal("399000"))
        );

        assertThat(transactions).hasSize(1);
        SePayTransactionCandidate transaction = transactions.get(0);
        assertThat(transaction.id()).isEqualTo("0f171a36-5a4e-4e00-b7fb-c8a4560d9c10");
        assertThat(transaction.transactionDate()).isEqualTo("2026-06-19T17:30:00+07:00");
        assertThat(transaction.accountNumber()).isEqualTo("123456789");
        assertThat(transaction.transferType()).isEqualTo("in");
        assertThat(transaction.amountIn()).isEqualByComparingTo("399000");
        assertThat(transaction.transactionContent()).isEqualTo("Thanh toan SLPABC123DEF456");
        assertThat(transaction.referenceNumber()).isEqualTo("FT24012345678");
        assertThat(transaction.code()).isEqualTo("SLPABC123DEF456");
        server.verify();
    }

    @Test
    void findTransactionsShouldRejectMissingTokenWithoutExposingSecrets() {
        SePayProperties sePayProperties = new SePayProperties();
        sePayProperties.setWebhookSecret("fake-webhook-secret");
        SystemSettingsService settingsService = mock(SystemSettingsService.class);
        when(settingsService.resolveSePayRuntimeSettings()).thenReturn(new SePayRuntimeSettings("", "fake-webhook-secret"));
        DefaultSePayTransactionClient client = new DefaultSePayTransactionClient(
                sePayProperties,
                settingsService,
                RestClient.builder()
        );

        assertThatThrownBy(() -> client.findTransactions(
                SePayTransactionQuery.forPaymentCode("SLPABC123DEF456", new BigDecimal("399000"))
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
                    assertThat(exception.getMessage()).doesNotContain("fake-webhook-secret");
                });
    }

    @Test
    void findTransactionsShouldExposeOnlyUpstreamStatusWhenSePayRejectsToken() {
        SePayProperties sePayProperties = new SePayProperties();
        sePayProperties.setApiBaseUrl("https://sepay.example.test");
        SystemSettingsService settingsService = mock(SystemSettingsService.class);
        when(settingsService.resolveSePayRuntimeSettings())
                .thenReturn(new SePayRuntimeSettings("sensitive-api-token", "fake-webhook-secret"));
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        DefaultSePayTransactionClient client = new DefaultSePayTransactionClient(
                sePayProperties,
                settingsService,
                restClientBuilder
        );
        server.expect(requestTo("https://sepay.example.test/v2/transactions"
                        + "?q=SLPABC123DEF456"
                        + "&transfer_type=in"
                        + "&amount_in_min=399000"
                        + "&amount_in_max=399000"
                        + "&per_page=20"
                        + "&timestamp_format=iso8601"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.findTransactions(
                SePayTransactionQuery.forPaymentCode("SLPABC123DEF456", new BigDecimal("399000"))
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
                    assertThat(exception.getMessage()).isEqualTo("SePay transaction service returned HTTP 401");
                    assertThat(exception.getMessage()).doesNotContain("sensitive-api-token");
                });
        server.verify();
    }
}
