package com.smartlearnly.backend.user.repository;

import com.smartlearnly.backend.user.entity.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserAccount, UUID>, JpaSpecificationExecutor<UserAccount> {
    Optional<UserAccount> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);
    Optional<UserAccount> findByGoogleIdAndDeletedAtIsNull(String googleId);
    Optional<UserAccount> findByAuthUserIdAndDeletedAtIsNull(UUID authUserId);
    Optional<UserAccount> findByIdAndDeletedAtIsNull(UUID id);
    Optional<UserAccount> findByIdAndRoleIgnoreCaseAndStatusIgnoreCaseAndDeletedAtIsNull(
            UUID id,
            String role,
            String status
    );

    @Query(
            value = """
                    SELECT user_account.*
                    FROM public.users user_account
                    WHERE user_account.deleted_at IS NULL
                      AND (:role IS NULL OR user_account.role::text = :role)
                      AND (:status IS NULL OR user_account.status::text = :status)
                      AND (
                          :keyword IS NULL
                          OR user_account.email ILIKE :keyword ESCAPE '\\'
                          OR user_account.full_name ILIKE :keyword ESCAPE '\\'
                          OR user_account.phone_number ILIKE :keyword ESCAPE '\\'
                      )
                    ORDER BY user_account.created_at DESC, user_account.email ASC
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM public.users user_account
                    WHERE user_account.deleted_at IS NULL
                      AND (:role IS NULL OR user_account.role::text = :role)
                      AND (:status IS NULL OR user_account.status::text = :status)
                      AND (
                          :keyword IS NULL
                          OR user_account.email ILIKE :keyword ESCAPE '\\'
                          OR user_account.full_name ILIKE :keyword ESCAPE '\\'
                          OR user_account.phone_number ILIKE :keyword ESCAPE '\\'
                      )
                    """,
            nativeQuery = true)
    Page<UserAccount> findAdminUsers(
            @Param("role") String role,
            @Param("status") String status,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
