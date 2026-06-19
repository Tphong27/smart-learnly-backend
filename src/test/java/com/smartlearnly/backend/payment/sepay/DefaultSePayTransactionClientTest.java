package com.smartlearnly.backend.payment.sepay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DefaultSePayTransactionClientTest {
    @Test
    void findTransactionsShouldCallDocumentedEndpointAndMapFields() {
        SePayProperties sePayProperties = new SePayProperties();
        sePayProperties.setApiToken("fake-api-token");
        sePayProperties.setApiBaseUrl("https://sepay.example.test/");
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        DefaultSePayTransactionClient client = new DefaultSePayTransactionClient(sePayProperties, restClientBuilder);
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
                          "transactions": [
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
                          ]
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
        DefaultSePayTransactionClient client = new DefaultSePayTransactionClient(
                sePayProperties,
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
}
