package com.smartlearnly.backend.payment.sepay;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SePayWebhookEventRepository {
    private final JdbcTemplate jdbcTemplate;

    public boolean saveReceivedEvent(
            long gatewayEventId,
            String signature,
            long eventTimestamp,
            String payload
    ) {
        int inserted = jdbcTemplate.update("""
                INSERT INTO public.sepay_webhook_events (
                    gateway_event_id,
                    signature,
                    event_timestamp,
                    payload,
                    processing_status
                )
                VALUES (?, ?, ?, CAST(? AS jsonb), 'RECEIVED')
                ON CONFLICT (gateway_event_id) DO NOTHING
                """,
                gatewayEventId,
                signature,
                eventTimestamp,
                payload);
        return inserted > 0;
    }
}
