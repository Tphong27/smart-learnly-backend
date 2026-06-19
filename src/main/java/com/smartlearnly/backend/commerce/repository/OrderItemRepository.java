package com.smartlearnly.backend.commerce.repository;

import com.smartlearnly.backend.commerce.entity.OrderItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
    List<OrderItem> findByOrderIdOrderByCreatedAtAsc(UUID orderId);
}
