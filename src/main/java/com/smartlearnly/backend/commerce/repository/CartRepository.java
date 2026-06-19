package com.smartlearnly.backend.commerce.repository;

import com.smartlearnly.backend.commerce.entity.Cart;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartRepository extends JpaRepository<Cart, UUID> {
    Optional<Cart> findByUserId(UUID userId);

    Optional<Cart> findByIdAndUserId(UUID id, UUID userId);
}
