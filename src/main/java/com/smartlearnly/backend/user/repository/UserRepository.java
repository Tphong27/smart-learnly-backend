package com.smartlearnly.backend.user.repository;

import com.smartlearnly.backend.user.entity.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);
    Optional<UserAccount> findByGoogleIdAndDeletedAtIsNull(String googleId);
    Optional<UserAccount> findByAuthUserIdAndDeletedAtIsNull(UUID authUserId);
    Optional<UserAccount> findByIdAndDeletedAtIsNull(UUID id);
    @Query(value = """
                   SELECT u.*
                   FROM public.users u
                   WHERE u.id = :id
                     AND u.deleted_at IS NULL
                     AND LOWER(CAST(u.role AS text)) = LOWER(:role)
                     AND LOWER(CAST(u.status AS text)) = LOWER(:status)
                   LIMIT 1
                   """, nativeQuery = true)
    Optional<UserAccount> findActiveUserByIdAndRole(
                @Param("id") UUID id,
                @Param("role") String role,
                @Param("status") String status
    );

    @Query(
            value = """
                    SELECT u.*
                    FROM public.users u
                    WHERE u.deleted_at IS NULL
                      AND (:role IS NULL OR CAST(u.role AS text) = :role)
                      AND (:status IS NULL OR CAST(u.status AS text) = :status)
                      AND (
                          :keyword IS NULL
                          OR u.email ILIKE :keyword ESCAPE '\\'
                          OR u.full_name ILIKE :keyword ESCAPE '\\'
                      )
                    ORDER BY u.full_name ASC, u.email ASC
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM public.users u
                    WHERE u.deleted_at IS NULL
                      AND (:role IS NULL OR CAST(u.role AS text) = :role)
                      AND (:status IS NULL OR CAST(u.status AS text) = :status)
                      AND (
                          :keyword IS NULL
                          OR u.email ILIKE :keyword ESCAPE '\\'
                          OR u.full_name ILIKE :keyword ESCAPE '\\'
                      )
                    """,
            nativeQuery = true)
    Page<UserAccount> searchAdminUsers(
            @Param("role") String role,
            @Param("status") String status,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
