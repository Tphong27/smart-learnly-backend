package com.smartlearnly.backend.user.repository;

import com.smartlearnly.backend.user.entity.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);
    Optional<UserAccount> findByAuthUserIdAndDeletedAtIsNull(UUID authUserId);
    Optional<UserAccount> findByIdAndDeletedAtIsNull(UUID id);
}
