package com.smartlearnly.backend.learning.module.repository;

import com.smartlearnly.backend.learning.module.entity.CourseSection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseSectionRepository extends JpaRepository<CourseSection, UUID> {
}
