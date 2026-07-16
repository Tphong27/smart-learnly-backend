package com.smartlearnly.backend.lessonprogress.repository;

import com.smartlearnly.backend.lessonprogress.entity.LessonProgress;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonProgressRepository extends JpaRepository<LessonProgress, UUID> {
    Optional<LessonProgress> findByStudentIdAndLessonId(UUID studentId, UUID lessonId);

    Optional<LessonProgress> findByStudentIdAndClassIdAndLessonIdentityId(
            UUID studentId,
            UUID classId,
            UUID lessonIdentityId);

    List<LessonProgress> findByStudentIdAndCourseId(UUID studentId, UUID courseId);

    List<LessonProgress> findByStudentIdAndClassIdAndCourseId(UUID studentId, UUID classId, UUID courseId);

    List<LessonProgress> findByStudentIdAndCourseIdIn(UUID studentId, Collection<UUID> courseIds);
    
    List<LessonProgress> findByStudentIdAndCourseIdAndClassIdIsNull(UUID studentId, UUID courseId);
}