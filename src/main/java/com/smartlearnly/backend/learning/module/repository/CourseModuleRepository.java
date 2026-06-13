package com.smartlearnly.backend.learning.module.repository;

import com.smartlearnly.backend.learning.module.entity.CourseModule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseModuleRepository extends JpaRepository<CourseModule, UUID> {
    List<CourseModule> findByCourse_IdOrderByOrderIndexAscCreatedAtAsc(UUID courseId);

    List<CourseModule> findByCourse_IdAndStatusOrderByOrderIndexAscCreatedAtAsc(UUID courseId, String status);

    Optional<CourseModule> findByIdAndCourse_Id(UUID id, UUID courseId);

    @Query("select coalesce(max(module.orderIndex), -1) from CourseModule module where module.course.id = :courseId")
    int findMaxOrderIndexByCourseId(@Param("courseId") UUID courseId);
}
