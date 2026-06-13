package com.smartlearnly.backend.course.repository;

import com.smartlearnly.backend.course.entity.Course;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, UUID> {
    boolean existsByCategoryId(UUID categoryId);
}
