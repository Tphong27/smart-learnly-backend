package com.smartlearnly.backend.learning.module.repository;

import com.smartlearnly.backend.learning.module.entity.CourseSection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseSectionRepository extends JpaRepository<CourseSection, UUID> {
    List<CourseSection> findByCourseIdOrderBySortOrderAscCreatedAtAsc(UUID courseId);

    Optional<CourseSection> findByIdAndCourseId(UUID id, UUID courseId);

    @Query("select coalesce(max(section.sortOrder), -1) from CourseSection section where section.course.id = :courseId")
    int findMaxSortOrderByCourseId(@Param("courseId") UUID courseId);
}
