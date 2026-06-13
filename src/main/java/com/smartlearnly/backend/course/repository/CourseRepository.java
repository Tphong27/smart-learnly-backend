package com.smartlearnly.backend.course.repository;

import com.smartlearnly.backend.course.entity.Course;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, UUID> {
    Page<Course> findAllByDeletedAtIsNull(Pageable pageable);

    Optional<Course> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsBySlugAndDeletedAtIsNull(String slug);

    boolean existsBySlugAndIdNotAndDeletedAtIsNull(String slug, UUID id);
}
