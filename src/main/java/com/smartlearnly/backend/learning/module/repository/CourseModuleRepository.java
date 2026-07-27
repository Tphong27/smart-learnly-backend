package com.smartlearnly.backend.learning.module.repository;

import com.smartlearnly.backend.learning.module.entity.CourseModule;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseModuleRepository extends JpaRepository<CourseModule, UUID> {
    Optional<CourseModule> findByIdAndCourseId(UUID id, UUID courseId);

    boolean existsByIdAndCourseIdAndSystemFalseAndStatus(
            UUID id,
            UUID courseId,
            String status
    );
}
