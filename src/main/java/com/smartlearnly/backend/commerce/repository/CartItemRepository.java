package com.smartlearnly.backend.commerce.repository;

import com.smartlearnly.backend.commerce.entity.CartItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {
    List<CartItem> findByCartIdOrderByAddedAtAsc(UUID cartId);

    Optional<CartItem> findByIdAndCartId(UUID id, UUID cartId);

    @Query("""
            select count(item) > 0
            from CartItem item
            where item.cartId = :cartId
              and item.courseId = :courseId
              and (
                  (:classId is null and item.classId is null)
                  or item.classId = :classId
              )
            """)
    boolean existsSameProduct(
            @Param("cartId") UUID cartId,
            @Param("courseId") UUID courseId,
            @Param("classId") UUID classId
    );

    @Modifying
    void deleteByCartId(UUID cartId);
}
