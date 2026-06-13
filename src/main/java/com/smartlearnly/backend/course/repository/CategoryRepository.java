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

    @Query("""
            SELECT category
            FROM Category category
            WHERE (:keyword IS NULL
                    OR LOWER(category.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(category.slug) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:active IS NULL OR category.active = :active)
              AND (:parentId IS NULL OR category.parent.id = :parentId)
            ORDER BY category.sortOrder ASC, LOWER(category.name) ASC
            """)
    List<Category> search(
            @Param("keyword") String keyword,
            @Param("active") Boolean active,
            @Param("parentId") UUID parentId
    );
}

