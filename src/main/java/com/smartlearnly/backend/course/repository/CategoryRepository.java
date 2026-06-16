package com.smartlearnly.backend.course.repository;

import com.smartlearnly.backend.course.entity.Category;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    Optional<Category> findBySlugIgnoreCase(String slug);
    boolean existsBySlugIgnoreCase(String slug);
    boolean existsBySlug(String slug);
    boolean existsBySlugIgnoreCaseAndIdNot(String slug, UUID id);
    boolean existsByParentId(UUID parentId);

    @Query(value = """
            SELECT category.*
            FROM public.categories category
            WHERE (CAST(:keyword AS text) IS NULL
                    OR LOWER(category.name) LIKE LOWER(CONCAT('%', CAST(:keyword AS text), '%'))
                    OR LOWER(category.slug) LIKE LOWER(CONCAT('%', CAST(:keyword AS text), '%')))
              AND (CAST(:active AS boolean) IS NULL OR category.is_active = CAST(:active AS boolean))
              AND (CAST(:parentId AS uuid) IS NULL OR category.parent_id = CAST(:parentId AS uuid))
            ORDER BY category.sort_order ASC, LOWER(category.name) ASC
            """, nativeQuery = true)
    List<Category> search(
            @Param("keyword") String keyword,
            @Param("active") Boolean active,
            @Param("parentId") UUID parentId
    );

    @Query(value = """
            SELECT category.*
            FROM public.categories category
            LEFT JOIN public.categories parent ON parent.id = category.parent_id
            WHERE category.is_active = true
              AND (parent.id IS NULL OR parent.is_active = true)
              AND (CAST(:keyword AS text) IS NULL
                    OR LOWER(category.name) LIKE LOWER(CONCAT('%', CAST(:keyword AS text), '%'))
                    OR LOWER(category.slug) LIKE LOWER(CONCAT('%', CAST(:keyword AS text), '%')))
              AND (CAST(:parentId AS uuid) IS NULL OR parent.id = CAST(:parentId AS uuid))
            ORDER BY category.sort_order ASC, LOWER(category.name) ASC
            """, nativeQuery = true)
    List<Category> searchPublicActive(
            @Param("keyword") String keyword,
            @Param("parentId") UUID parentId
    );
}

