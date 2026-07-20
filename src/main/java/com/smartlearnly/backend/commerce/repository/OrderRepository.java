package com.smartlearnly.backend.commerce.repository;

import com.smartlearnly.backend.commerce.entity.PurchaseOrder;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<PurchaseOrder, UUID> {
    boolean existsByOrderCode(String orderCode);

    Optional<PurchaseOrder> findByIdAndUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select purchaseOrder from PurchaseOrder purchaseOrder where purchaseOrder.id = :id")
    Optional<PurchaseOrder> findByIdForUpdate(@Param("id") UUID id);

    @Query(
            value = """
                    select order_record.*
                    from public.orders order_record
                    where (
                          cast(:status as text) is null
                          or order_record.status = cast(:status as text)
                      )
                      and (
                          cast(:keyword as text) is null
                          or order_record.order_code ilike concat('%', cast(:keyword as text), '%')
                          or cast(order_record.id as text) ilike concat('%', cast(:keyword as text), '%')
                      )
                    order by order_record.created_at desc
                    """,
            countQuery = """
                    select count(*)
                    from public.orders order_record
                    where (
                          cast(:status as text) is null
                          or order_record.status = cast(:status as text)
                      )
                      and (
                          cast(:keyword as text) is null
                          or order_record.order_code ilike concat('%', cast(:keyword as text), '%')
                          or cast(order_record.id as text) ilike concat('%', cast(:keyword as text), '%')
                      )
                    """,
            nativeQuery = true)
    Page<PurchaseOrder> searchAll(
            @Param("keyword") String keyword,
            @Param("status") String status,
            Pageable pageable);

    @Query(
            value = """
                    select order_record.id
                    from public.orders order_record
                    where order_record.status = 'PENDING'
                      and order_record.expires_at < :now
                    order by order_record.expires_at asc
                    limit :limit
                    """,
            nativeQuery = true)
    List<UUID> findDuePendingOrderIds(@Param("now") Instant now, @Param("limit") int limit);
}
