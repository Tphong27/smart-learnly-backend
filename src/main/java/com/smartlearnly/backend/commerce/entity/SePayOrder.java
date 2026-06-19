package com.smartlearnly.backend.commerce.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "sepay_orders", schema = "public")
public class SePayOrder {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "payment_code", nullable = false)
    private String paymentCode;

    @Column(name = "bank_account_number", nullable = false)
    private String bankAccountNumber;

    @Column(name = "bank_name", nullable = false)
    private String bankName;

    @Column(name = "account_name", nullable = false)
    private String accountName;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "qr_url", nullable = false)
    private String qrUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SePayOrderStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "matched_at")
    private Instant matchedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (status == null) {
            status = SePayOrderStatus.CREATED;
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
