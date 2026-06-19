package com.smartlearnly.backend.payment.sepay;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SePayInvoiceNumberRepository {
    private final JdbcTemplate jdbcTemplate;

    public String nextInvoiceNumber() {
        Long sequenceValue = jdbcTemplate.queryForObject(
                "SELECT nextval('public.invoice_number_seq')",
                Long.class
        );
        return "SLP-INV-%010d".formatted(sequenceValue == null ? 0L : sequenceValue);
    }
}
