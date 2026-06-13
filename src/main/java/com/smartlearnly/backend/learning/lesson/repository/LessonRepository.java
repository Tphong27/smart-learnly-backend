package com.smartlearnly.backend.learning.lesson.repository;

import com.smartlearnly.backend.learning.lesson.entity.Lesson;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {
    List<Lesson> findByModule_IdOrderByOrderIndexAscCreatedAtAsc(UUID moduleId);

    List<Lesson> findByModule_IdAndStatusOrderByOrderIndexAscCreatedAtAsc(UUID moduleId, String status);

    Optional<Lesson> findByIdAndModule_Id(UUID id, UUID moduleId);

    @Query("select coalesce(max(lesson.orderIndex), -1) from Lesson lesson where lesson.module.id = :moduleId")
    int findMaxOrderIndexByModuleId(@Param("moduleId") UUID moduleId);

    @Query("""
            select lesson
            from Lesson lesson
            join fetch lesson.module module
            join fetch module.course course
            where course.id = :courseId
              and course.deletedAt is null
              and course.status = 'published'
              and module.status = 'active'
              and lesson.status = 'active'
              and lesson.preview = true
            order by module.orderIndex asc, lesson.orderIndex asc, lesson.createdAt asc
            """)
    List<Lesson> findPreviewLessonsForPublishedCourse(@Param("courseId") UUID courseId);

    @Query("""
            select lesson
            from Lesson lesson
            join fetch lesson.module module
            join fetch module.course course
            where course.id = :courseId
              and lesson.id = :lessonId
              and course.deletedAt is null
              and course.status = 'published'
              and module.status = 'active'
              and lesson.status = 'active'
              and lesson.preview = true
            """)
    Optional<Lesson> findPreviewLessonForPublishedCourse(
            @Param("courseId") UUID courseId,
            @Param("lessonId") UUID lessonId
    );
}
