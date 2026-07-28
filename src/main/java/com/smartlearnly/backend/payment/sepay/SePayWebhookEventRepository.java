package com.smartlearnly.backend.payment.sepay;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

    public long countEvents(String status) {
        String normalizedStatus = normalizeStatus(status);
        if (normalizedStatus == null) {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM public.sepay_webhook_events",
                    Long.class);
            return count == null ? 0L : count;
        }

        Long count = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM public.sepay_webhook_events
                WHERE processing_status = ?
                """,
                Long.class,
                normalizedStatus);
        return count == null ? 0L : count;
    }

    public List<SePayWebhookEventResponse> findEvents(String status, int limit, int offset) {
        String normalizedStatus = normalizeStatus(status);
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT id,
                       gateway_event_id,
                       processing_status,
                       failure_reason,
                       received_at,
                       processed_at
                FROM public.sepay_webhook_events
                """);

        if (normalizedStatus != null) {
            sql.append(" WHERE processing_status = ? ");
            args.add(normalizedStatus);
        }

        sql.append(" ORDER BY received_at DESC LIMIT ? OFFSET ? ");
        args.add(limit);
        args.add(offset);

        return jdbcTemplate.query(
                sql.toString(),
                (resultSet, rowNumber) -> new SePayWebhookEventResponse(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getLong("gateway_event_id"),
                        resultSet.getString("processing_status"),
                        resultSet.getString("failure_reason"),
                        toInstant(resultSet.getTimestamp("received_at")),
                        toInstant(resultSet.getTimestamp("processed_at"))
                ),
                args.toArray());
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return status.trim().toUpperCase();
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
