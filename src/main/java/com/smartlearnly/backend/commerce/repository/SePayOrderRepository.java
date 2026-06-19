package com.smartlearnly.backend.commerce.repository;

import com.smartlearnly.backend.commerce.entity.SePayOrder;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SePayOrderRepository extends JpaRepository<SePayOrder, UUID> {
    Optional<SePayOrder> findByOrderId(UUID orderId);

    Optional<SePayOrder> findByTransactionId(UUID transactionId);

    boolean existsByPaymentCode(String paymentCode);
}
