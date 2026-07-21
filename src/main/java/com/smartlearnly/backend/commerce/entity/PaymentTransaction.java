package com.smartlearnly.backend.commerce.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "transactions", schema = "public")
public class PaymentTransaction {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "course_id")
    private UUID courseId;

    @Column(name = "class_id")
    private UUID classId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    // Dùng varchar + converter để Hibernate bind string trong WHERE (tránh
    // Postgres "operator does not exist: tx_status = character varying").
    @Convert(converter = TransactionStatusConverter.class)
    @Column(nullable = false, length = 32)
    private TransactionStatus status;

    @Convert(converter = PaymentGatewayConverter.class)
    @Column(name = "payment_gateway", length = 32)
    private PaymentGateway paymentGateway;

    @Column(name = "gateway_event_id")
    private Long gatewayEventId;

    @Column(name = "gateway_transaction_id")
    private String gatewayTransactionId;

    @Column(name = "invoice_number")
    private String invoiceNumber;

    @Column(name = "invoice_url")
    private String invoiceUrl;

    @Column
    private String description;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (currency == null) {
            currency = "VND";
        }
        if (status == null) {
            status = TransactionStatus.PENDING;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
