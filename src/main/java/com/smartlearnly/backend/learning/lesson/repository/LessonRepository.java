package com.smartlearnly.backend.learning.lesson.repository;

import com.smartlearnly.backend.learning.lesson.entity.Lesson;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {
}
