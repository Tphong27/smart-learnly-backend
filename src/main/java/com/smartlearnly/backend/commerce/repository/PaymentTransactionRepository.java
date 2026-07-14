package com.smartlearnly.backend.commerce.repository;

import com.smartlearnly.backend.commerce.entity.PaymentTransaction;
import com.smartlearnly.backend.commerce.entity.TransactionStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {
    Page<PaymentTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<PaymentTransaction> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Optional<PaymentTransaction> findByIdAndUserId(UUID id, UUID userId);

    Optional<PaymentTransaction> findFirstByOrderIdOrderByCreatedAtDesc(UUID orderId);

    List<PaymentTransaction> findByOrderIdAndStatus(UUID orderId, TransactionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select paymentTransaction from PaymentTransaction paymentTransaction where paymentTransaction.id = :id")
    Optional<PaymentTransaction> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByGatewayTransactionIdAndIdNot(String gatewayTransactionId, UUID id);
}