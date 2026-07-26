package com.smartlearnly.backend.course.repository;

import com.smartlearnly.backend.course.entity.Course;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, UUID>, JpaSpecificationExecutor<Course> {
    boolean existsByCategory_Id(UUID categoryId);

    @Query(value = """
            SELECT
                c.id AS "id",
                c.title AS "title",
                c.slug AS "slug",
                c.description AS "description",
                c.price AS "price",
                c.discounted_price AS "discountedPrice",
                c.thumbnail_url AS "avatarUrl",
                c.is_featured AS "featured",
                category.id AS "categoryId",
                category.name AS "categoryName",
                category.slug AS "categorySlug"
            FROM public.courses c
            JOIN public.categories category ON category.id = c.category_id
            WHERE c.status = 'published'::public.course_status
              AND c.deleted_at IS NULL
            ORDER BY c.is_featured DESC, c.created_at DESC, c.id ASC
            """, countQuery = """
            SELECT COUNT(*)
            FROM public.courses c
            JOIN public.categories category ON category.id = c.category_id
            WHERE c.status = 'published'::public.course_status
              AND c.deleted_at IS NULL
            """, nativeQuery = true)
    Page<CourseListProjection> findPublishedCourses(Pageable pageable);

    @Query(value = """
            SELECT
                c.id AS "id",
                c.title AS "title",
                c.slug AS "slug",
                c.description AS "description",
                c.price AS "price",
                c.discounted_price AS "discountedPrice",
                c.thumbnail_url AS "avatarUrl",
                c.is_featured AS "featured",
                category.id AS "categoryId",
                category.name AS "categoryName",
                category.slug AS "categorySlug"
            FROM public.courses c
            JOIN public.categories category
                ON category.id = c.category_id
            WHERE c.status = 'published'::public.course_status
              AND c.deleted_at IS NULL
              AND (
                  :searchPattern IS NULL
                  OR c.title ILIKE :searchPattern ESCAPE '\\'
                  OR COALESCE(c.description, '') ILIKE :searchPattern ESCAPE '\\'
              )
              AND (
                  :categorySlug IS NULL
                  OR category.slug = :categorySlug
              )
              AND (
                  :minPrice IS NULL
                  OR CASE
                      WHEN c.discounted_price IS NOT NULL
                          AND c.discounted_price > 0
                          AND c.discounted_price < c.price
                      THEN c.discounted_price
                      ELSE c.price
                  END >= :minPrice
              )
              AND (
                  :maxPrice IS NULL
                  OR CASE
                      WHEN c.discounted_price IS NOT NULL
                          AND c.discounted_price > 0
                          AND c.discounted_price < c.price
                      THEN c.discounted_price
                      ELSE c.price
                  END <= :maxPrice
              )
              AND (
                  :onSale = FALSE
                  OR (
                      c.price > 0
                      AND c.discounted_price IS NOT NULL
                      AND c.discounted_price > 0
                      AND c.discounted_price < c.price
                  )
              )
              AND (
                  :featured IS NULL
                  OR c.is_featured = :featured
              )
            ORDER BY
                CASE
                    WHEN :sort = 'PRICE_ASC'
                    THEN CASE
                        WHEN c.discounted_price IS NOT NULL
                            AND c.discounted_price > 0
                            AND c.discounted_price < c.price
                        THEN c.discounted_price
                        ELSE c.price
                    END
                END ASC,
                CASE
                    WHEN :sort = 'PRICE_DESC'
                    THEN CASE
                        WHEN c.discounted_price IS NOT NULL
                            AND c.discounted_price > 0
                            AND c.discounted_price < c.price
                        THEN c.discounted_price
                        ELSE c.price
                    END
                END DESC,
                CASE
                    WHEN :sort = 'POPULAR'
                    THEN (
                        SELECT COUNT(*)
                        FROM public.course_enrollments enrollment
                        WHERE enrollment.course_id = c.id
                          AND enrollment.status IN (
                              'active'::public.enroll_status,
                              'completed'::public.enroll_status
                          )
                    )
                END DESC,
                CASE
                    WHEN :sort = 'POPULAR'
                    THEN c.is_featured
                END DESC,
                c.created_at DESC,
                c.id ASC
            """, countQuery = """
            SELECT COUNT(*)
            FROM public.courses c
            JOIN public.categories category
                ON category.id = c.category_id
            WHERE c.status = 'published'::public.course_status
              AND c.deleted_at IS NULL
              AND (
                  :searchPattern IS NULL
                  OR c.title ILIKE :searchPattern ESCAPE '\\'
                  OR COALESCE(c.description, '') ILIKE :searchPattern ESCAPE '\\'
              )
              AND (
                  :categorySlug IS NULL
                  OR category.slug = :categorySlug
              )
              AND (
                  :minPrice IS NULL
                  OR CASE
                      WHEN c.discounted_price IS NOT NULL
                          AND c.discounted_price > 0
                          AND c.discounted_price < c.price
                      THEN c.discounted_price
                      ELSE c.price
                  END >= :minPrice
              )
              AND (
                  :maxPrice IS NULL
                  OR CASE
                      WHEN c.discounted_price IS NOT NULL
                          AND c.discounted_price > 0
                          AND c.discounted_price < c.price
                      THEN c.discounted_price
                      ELSE c.price
                  END <= :maxPrice
              )
              AND (
                  :onSale = FALSE
                  OR (
                      c.price > 0
                      AND c.discounted_price IS NOT NULL
                      AND c.discounted_price > 0
                      AND c.discounted_price < c.price
                  )
              )
              AND (
                  :featured IS NULL
                  OR c.is_featured = :featured
              )
            """, nativeQuery = true)
    Page<CourseListProjection> findPublishedCoursesByFilters(
            @Param("searchPattern") String searchPattern,
            @Param("categorySlug") String categorySlug,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("onSale") boolean onSale,
            @Param("featured") Boolean featured,
            @Param("sort") String sort,
            Pageable pageable);

    @Query(value = """
            SELECT
                c.id AS "id",
                c.title AS "title",
                c.slug AS "slug",
                c.description AS "description",
                c.price AS "price",
                c.discounted_price AS "discountedPrice",
                c.thumbnail_url AS "avatarUrl",
                c.is_featured AS "featured",
                category.id AS "categoryId",
                category.name AS "categoryName",
                category.slug AS "categorySlug"
            FROM public.courses c
            JOIN public.categories category ON category.id = c.category_id
            WHERE c.status = 'published'::public.course_status
              AND c.deleted_at IS NULL
              AND (
                  c.title ILIKE :searchPattern ESCAPE '\\'
                  OR COALESCE(c.description, '') ILIKE :searchPattern ESCAPE '\\'
              )
            ORDER BY c.is_featured DESC, c.created_at DESC, c.id ASC
            """, countQuery = """
            SELECT COUNT(*)
            FROM public.courses c
            JOIN public.categories category ON category.id = c.category_id
            WHERE c.status = 'published'::public.course_status
              AND c.deleted_at IS NULL
              AND (
                  c.title ILIKE :searchPattern ESCAPE '\\'
                  OR COALESCE(c.description, '') ILIKE :searchPattern ESCAPE '\\'
              )
            """, nativeQuery = true)
    Page<CourseListProjection> searchPublishedCourses(
            @Param("searchPattern") String searchPattern,
            Pageable pageable);

    @Query(value = """
            SELECT
                c.id AS "id",
                c.title AS "title",
                c.slug AS "slug",
                c.description AS "description",
                c.price AS "price",
                c.discounted_price AS "discountedPrice",
                c.thumbnail_url AS "avatarUrl",
                c.is_featured AS "featured",
                category.id AS "categoryId",
                category.name AS "categoryName",
                category.slug AS "categorySlug"
            FROM public.courses c
            JOIN public.categories category ON category.id = c.category_id
            WHERE c.status = 'published'::public.course_status
              AND c.deleted_at IS NULL
              AND category.slug = :categorySlug
            ORDER BY c.is_featured DESC, c.created_at DESC, c.id ASC
            """, countQuery = """
            SELECT COUNT(*)
            FROM public.courses c
            JOIN public.categories category ON category.id = c.category_id
            WHERE c.status = 'published'::public.course_status
              AND c.deleted_at IS NULL
              AND category.slug = :categorySlug
            """, nativeQuery = true)
    Page<CourseListProjection> findPublishedCoursesByCategorySlug(
            @Param("categorySlug") String categorySlug,
            Pageable pageable);

    @Query(value = """
            SELECT
                c.id AS "id",
                c.title AS "title",
                c.slug AS "slug",
                c.description AS "description",
                c.price AS "price",
                c.discounted_price AS "discountedPrice",
                c.thumbnail_url AS "avatarUrl",
                c.is_featured AS "featured",
                category.id AS "categoryId",
                category.name AS "categoryName",
                category.slug AS "categorySlug"
            FROM public.courses c
            JOIN public.categories category ON category.id = c.category_id
            WHERE c.slug = :slug
              AND c.status = 'published'::public.course_status
              AND c.deleted_at IS NULL
            """, nativeQuery = true)
    Optional<CourseDetailProjection> findPublishedCourseBySlug(@Param("slug") String slug);

    @Query(value = """
            SELECT
                c.id AS "id",
                c.title AS "title",
                c.slug AS "slug",
                c.description AS "description",
                c.price AS "price",
                c.discounted_price AS "discountedPrice",
                c.thumbnail_url AS "avatarUrl",
                c.is_featured AS "featured",
                category.id AS "categoryId",
                category.name AS "categoryName",
                category.slug AS "categorySlug"
            FROM public.courses c
            JOIN public.categories category ON category.id = c.category_id
            WHERE c.id = :id
              AND c.status = 'published'::public.course_status
              AND c.deleted_at IS NULL
            """, nativeQuery = true)
    Optional<CourseDetailProjection> findPublishedCourseById(@Param("id") UUID id);

    @Query(value = """
            SELECT
                clo.id AS "id",
                clo.code AS "code",
                clo.description AS "description"
            FROM public.course_learning_objectives clo
            WHERE clo.course_id = :courseId
            ORDER BY clo.code ASC, clo.id ASC
            """, nativeQuery = true)
    List<LearningObjectiveProjection> findLearningObjectivesByCourseId(@Param("courseId") UUID courseId);

    @Query(value = """
                                SELECT
                                    m.id AS "moduleId",
                                    m.title AS "moduleTitle",
                                    m.order_index AS "moduleOrderIndex",
                                    l.id AS "lessonId",
                                    l.title AS "lessonTitle",
                                    l.lesson_type::text AS "lessonType",
                                    l.order_index AS "lessonOrderIndex",
                                    l.is_preview AS "lessonPreview"
                                FROM public.modules m
                                LEFT JOIN public.lessons l
                ON l.module_id = m.id
               AND l.status = 'published'::public.lesson_status
            WHERE m.course_id = :courseId
              AND m.status = 'active'
                                ORDER BY
                                    m.order_index ASC,
                                    m.id ASC,
                                    l.order_index ASC,
                                    l.id ASC
                                """, nativeQuery = true)
    List<CurriculumRowProjection> findActiveCurriculumByCourseId(@Param("courseId") UUID courseId);

    boolean existsBySlugIgnoreCaseAndDeletedAtIsNull(String slug);

    boolean existsBySlugIgnoreCaseAndIdNotAndDeletedAtIsNull(String slug, UUID id);

    Optional<Course> findByIdAndDeletedAtIsNull(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select course from Course course where course.id = :id and course.deletedAt is null")
    Optional<Course> findByIdAndDeletedAtIsNullForUpdate(@Param("id") UUID id);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM public.courses c
                WHERE c.id = :id
                  AND c.status = 'published'::public.course_status
                  AND c.deleted_at IS NULL
            )
            """, nativeQuery = true)
    boolean existsPublishedById(@Param("id") UUID id);

    Page<Course> findAllByDeletedAtIsNull(Pageable pageable);

    @Query(value = """
            SELECT course.*
            FROM public.courses course
            WHERE course.deleted_at IS NULL
              AND EXISTS (
                  SELECT 1
                  FROM public.classes class_offering
                  WHERE class_offering.course_id = course.id
                    AND class_offering.trainer_id = :trainerId
                    AND class_offering.deleted_at IS NULL
              )
            ORDER BY course.created_at DESC, course.id ASC
            """, countQuery = """
            SELECT COUNT(*)
            FROM public.courses course
            WHERE course.deleted_at IS NULL
              AND EXISTS (
                  SELECT 1
                  FROM public.classes class_offering
                  WHERE class_offering.course_id = course.id
                    AND class_offering.trainer_id = :trainerId
                    AND class_offering.deleted_at IS NULL
              )
            """, nativeQuery = true)
    Page<Course> findAllAssignedToTrainer(
            @Param("trainerId") UUID trainerId,
            Pageable pageable);

    boolean existsByIdAndAssignedSme_IdAndDeletedAtIsNull(UUID courseId, UUID smeId);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM public.classes class_offering
                WHERE class_offering.course_id = :courseId
                  AND class_offering.trainer_id = :trainerId
                  AND class_offering.deleted_at IS NULL
            )
            """, nativeQuery = true)
    boolean existsTrainerAssignment(
            @Param("courseId") UUID courseId,
            @Param("trainerId") UUID trainerId);
}
