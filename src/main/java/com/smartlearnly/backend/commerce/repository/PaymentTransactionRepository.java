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

public interface PaymentTransactionRepository
        extends JpaRepository<PaymentTransaction, UUID> {

    Page<PaymentTransaction> findByUserIdOrderByCreatedAtDesc(
            UUID userId,
            Pageable pageable);

    Page<PaymentTransaction> findAllByOrderByCreatedAtDesc(
            Pageable pageable);

    @Query(value = """
            select transaction_record.*
            from public.transactions transaction_record
            where transaction_record.user_id = :userId
              and (
                  cast(:keyword as text) is null
                  or coalesce(
                      transaction_record.invoice_number,
                      ''
                  ) ilike concat(
                      '%',
                      cast(:keyword as text),
                      '%'
                  )
                  or cast(
                      transaction_record.id as text
                  ) ilike concat(
                      '%',
                      cast(:keyword as text),
                      '%'
                  )
                  or cast(
                      transaction_record.order_id as text
                  ) ilike concat(
                      '%',
                      cast(:keyword as text),
                      '%'
                  )
              )
              and (
                  cast(:status as text) is null
                  or cast(
                      transaction_record.status as text
                  ) = cast(:status as text)
              )
              and (
                  cast(:paymentGateway as text) is null
                  or cast(
                      transaction_record.payment_gateway as text
                  ) = cast(:paymentGateway as text)
              )
              and (
                  cast(:currency as text) is null
                  or upper(transaction_record.currency)
                      = upper(cast(:currency as text))
              )
            order by transaction_record.created_at desc
            """, countQuery = """
            select count(*)
            from public.transactions transaction_record
            where transaction_record.user_id = :userId
              and (
                  cast(:keyword as text) is null
                  or coalesce(
                      transaction_record.invoice_number,
                      ''
                  ) ilike concat(
                      '%',
                      cast(:keyword as text),
                      '%'
                  )
                  or cast(
                      transaction_record.id as text
                  ) ilike concat(
                      '%',
                      cast(:keyword as text),
                      '%'
                  )
                  or cast(
                      transaction_record.order_id as text
                  ) ilike concat(
                      '%',
                      cast(:keyword as text),
                      '%'
                  )
              )
              and (
                  cast(:status as text) is null
                  or cast(
                      transaction_record.status as text
                  ) = cast(:status as text)
              )
              and (
                  cast(:paymentGateway as text) is null
                  or cast(
                      transaction_record.payment_gateway as text
                  ) = cast(:paymentGateway as text)
              )
              and (
                  cast(:currency as text) is null
                  or upper(transaction_record.currency)
                      = upper(cast(:currency as text))
              )
            """, nativeQuery = true)
    Page<PaymentTransaction> searchByUserId(
            @Param("userId") UUID userId,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("paymentGateway") String paymentGateway,
            @Param("currency") String currency,
            Pageable pageable);

    @Query(value = """
            select transaction_record.*
            from public.transactions transaction_record
            where (
                  cast(:keyword as text) is null
                  or coalesce(
                      transaction_record.invoice_number,
                      ''
                  ) ilike concat(
                      '%',
                      cast(:keyword as text),
                      '%'
                  )
                  or cast(
                      transaction_record.id as text
                  ) ilike concat(
                      '%',
                      cast(:keyword as text),
                      '%'
                  )
                  or cast(
                      transaction_record.order_id as text
                  ) ilike concat(
                      '%',
                      cast(:keyword as text),
                      '%'
                  )
              )
              and (
                  cast(:status as text) is null
                  or cast(
                      transaction_record.status as text
                  ) = cast(:status as text)
              )
              and (
                  cast(:paymentGateway as text) is null
                  or cast(
                      transaction_record.payment_gateway as text
                  ) = cast(:paymentGateway as text)
              )
              and (
                  cast(:currency as text) is null
                  or upper(transaction_record.currency)
                      = upper(cast(:currency as text))
              )
            order by transaction_record.created_at desc
            """, countQuery = """
            select count(*)
            from public.transactions transaction_record
            where (
                  cast(:keyword as text) is null
                  or coalesce(
                      transaction_record.invoice_number,
                      ''
                  ) ilike concat(
                      '%',
                      cast(:keyword as text),
                      '%'
                  )
                  or cast(
                      transaction_record.id as text
                  ) ilike concat(
                      '%',
                      cast(:keyword as text),
                      '%'
                  )
                  or cast(
                      transaction_record.order_id as text
                  ) ilike concat(
                      '%',
                      cast(:keyword as text),
                      '%'
                  )
              )
              and (
                  cast(:status as text) is null
                  or cast(
                      transaction_record.status as text
                  ) = cast(:status as text)
              )
              and (
                  cast(:paymentGateway as text) is null
                  or cast(
                      transaction_record.payment_gateway as text
                  ) = cast(:paymentGateway as text)
              )
              and (
                  cast(:currency as text) is null
                  or upper(transaction_record.currency)
                      = upper(cast(:currency as text))
              )
            """, nativeQuery = true)
    Page<PaymentTransaction> searchAll(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("paymentGateway") String paymentGateway,
            @Param("currency") String currency,
            Pageable pageable);

    @Query(value = """
            select distinct
                cast(transaction_record.status as text)
            from public.transactions transaction_record
            where transaction_record.status is not null
            order by 1
            """, nativeQuery = true)
    List<String> findDistinctStatuses();

    @Query(value = """
            select distinct
                cast(transaction_record.payment_gateway as text)
            from public.transactions transaction_record
            where transaction_record.payment_gateway is not null
            order by 1
            """, nativeQuery = true)
    List<String> findDistinctPaymentGateways();

    @Query(value = """
            select distinct
                upper(trim(transaction_record.currency))
            from public.transactions transaction_record
            where transaction_record.currency is not null
              and trim(transaction_record.currency) <> ''
            order by 1
            """, nativeQuery = true)
    List<String> findDistinctCurrencies();

    Optional<PaymentTransaction> findByIdAndUserId(
            UUID id,
            UUID userId);

    Optional<PaymentTransaction> findFirstByOrderIdOrderByCreatedAtDesc(UUID orderId);

    List<PaymentTransaction> findByOrderIdAndStatus(
            UUID orderId,
            TransactionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select paymentTransaction
            from PaymentTransaction paymentTransaction
            where paymentTransaction.id = :id
            """)
    Optional<PaymentTransaction> findByIdForUpdate(
            @Param("id") UUID id);

    boolean existsByGatewayTransactionIdAndIdNot(
            String gatewayTransactionId,
            UUID id);
}