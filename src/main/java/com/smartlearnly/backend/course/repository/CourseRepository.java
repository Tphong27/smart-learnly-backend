package com.smartlearnly.backend.course.repository;

import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.entity.CourseStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, UUID> {
    boolean existsByCategoryId(UUID categoryId);
    boolean existsBySlugIgnoreCaseAndDeletedAtIsNull(String slug);
    boolean existsBySlugIgnoreCaseAndIdNotAndDeletedAtIsNull(String slug, UUID id);

    Optional<Course> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Course> findByIdAndStatusAndDeletedAtIsNull(UUID id, CourseStatus status);

    Page<Course> findAllByDeletedAtIsNull(Pageable pageable);
}
