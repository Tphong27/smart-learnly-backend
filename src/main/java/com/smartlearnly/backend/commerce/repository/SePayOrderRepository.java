package com.smartlearnly.backend.commerce.repository;

import com.smartlearnly.backend.commerce.entity.SePayOrder;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SePayOrderRepository extends JpaRepository<SePayOrder, UUID> {
    Optional<SePayOrder> findByOrderId(UUID orderId);

    Optional<SePayOrder> findByTransactionId(UUID transactionId);

    boolean existsByPaymentCode(String paymentCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sePayOrder from SePayOrder sePayOrder where sePayOrder.paymentCode = :paymentCode")
    Optional<SePayOrder> findByPaymentCodeForUpdate(@Param("paymentCode") String paymentCode);
}
