package com.smartlearnly.backend.commerce.repository;

import com.smartlearnly.backend.commerce.entity.PaymentTransaction;
import com.smartlearnly.backend.commerce.entity.TransactionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {
    Page<PaymentTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<PaymentTransaction> findByIdAndUserId(UUID id, UUID userId);

    Optional<PaymentTransaction> findFirstByOrderIdOrderByCreatedAtDesc(UUID orderId);

    List<PaymentTransaction> findByOrderIdAndStatus(UUID orderId, TransactionStatus status);
}
