package com.smartlearnly.backend.lessonprogress.repository;

import com.smartlearnly.backend.lessonprogress.entity.LessonProgress;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonProgressRepository extends JpaRepository<LessonProgress, UUID> {
    Optional<LessonProgress> findByStudentIdAndLessonId(UUID studentId, UUID lessonId);

    List<LessonProgress> findByStudentIdAndCourseId(UUID studentId, UUID courseId);

    List<LessonProgress> findByStudentIdAndCourseIdIn(UUID studentId, Collection<UUID> courseIds);
}