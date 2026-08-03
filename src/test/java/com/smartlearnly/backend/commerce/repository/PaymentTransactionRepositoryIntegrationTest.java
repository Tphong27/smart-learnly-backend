package com.smartlearnly.backend.commerce.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PaymentTransactionRepositoryIntegrationTest {
    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("drop table if exists public.transactions");
        jdbcTemplate.execute("""
                create table public.transactions (
                    id uuid primary key,
                    status varchar(50),
                    payment_gateway varchar(50),
                    currency varchar(10)
                )
                """);
    }

    @Test
    void findDistinctFiltersShouldReturnNormalizedOrderedValues() {
        insertTransaction("PENDING", "SEPAY", "vnd");
        insertTransaction("SUCCESS", "SEPAY", " VND ");
        insertTransaction("SUCCESS", "VNPAY", "usd");
        insertTransaction(null, null, "   ");
        insertTransaction("FAILED", "MANUAL", null);

        List<String> statuses = paymentTransactionRepository.findDistinctStatuses();
        List<String> paymentGateways = paymentTransactionRepository.findDistinctPaymentGateways();
        List<String> currencies = paymentTransactionRepository.findDistinctCurrencies();

        assertThat(statuses).containsExactly("FAILED", "PENDING", "SUCCESS");
        assertThat(paymentGateways).containsExactly("MANUAL", "SEPAY", "VNPAY");
        assertThat(currencies).containsExactly("USD", "VND");
    }

    private void insertTransaction(String status, String paymentGateway, String currency) {
        jdbcTemplate.update(
                "insert into public.transactions (id, status, payment_gateway, currency) values (?, ?, ?, ?)",
                UUID.randomUUID(),
                status,
                paymentGateway,
                currency);
    }
}
