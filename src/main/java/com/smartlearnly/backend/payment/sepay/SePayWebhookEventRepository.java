package com.smartlearnly.backend.payment.sepay;

import java.util.List;
import java.util.Optional;
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

    public Optional<String> findProcessingStatusByGatewayEventIdForUpdate(long gatewayEventId) {
        List<String> statuses = jdbcTemplate.query("""
                SELECT processing_status
                FROM public.sepay_webhook_events
                WHERE gateway_event_id = ?
                FOR UPDATE
                """,
                (resultSet, rowNumber) -> resultSet.getString("processing_status"),
                gatewayEventId);
        return statuses.stream().findFirst();
    }

    public void markProcessed(long gatewayEventId) {
        jdbcTemplate.update("""
                UPDATE public.sepay_webhook_events
                SET processing_status = 'PROCESSED',
                    failure_reason = NULL,
                    processed_at = now()
                WHERE gateway_event_id = ?
                """,
                gatewayEventId);
    }

    public void markMismatched(long gatewayEventId, String reason) {
        jdbcTemplate.update("""
                UPDATE public.sepay_webhook_events
                SET processing_status = 'MISMATCHED',
                    failure_reason = ?,
                    processed_at = now()
                WHERE gateway_event_id = ?
                """,
                reason,
                gatewayEventId);
    }

    public void markFailed(long gatewayEventId, String reason) {
        jdbcTemplate.update("""
                UPDATE public.sepay_webhook_events
                SET processing_status = 'FAILED',
                    failure_reason = ?,
                    processed_at = now()
                WHERE gateway_event_id = ?
                """,
                reason,
                gatewayEventId);
    }
}
