package com.smartlearnly.backend.learning.lesson.repository;

import com.smartlearnly.backend.course.entity.CourseStatus;
import com.smartlearnly.backend.learning.lesson.entity.Lesson;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {
    List<Lesson> findBySectionIdOrderBySortOrderAscCreatedAtAsc(UUID sectionId);

    Optional<Lesson> findByIdAndSectionId(UUID id, UUID sectionId);

    @Query("select coalesce(max(lesson.sortOrder), -1) from Lesson lesson where lesson.section.id = :sectionId")
    int findMaxSortOrderBySectionId(@Param("sectionId") UUID sectionId);

    @Query("""
            select lesson
            from Lesson lesson
            join fetch lesson.course course
            join fetch lesson.section section
            where course.id = :courseId
              and course.status = :courseStatus
              and course.deletedAt is null
              and lesson.status = :lessonStatus
              and lesson.preview = true
            order by section.sortOrder asc, lesson.sortOrder asc, lesson.createdAt asc
            """)
    List<Lesson> findPreviewLessons(
            @Param("courseId") UUID courseId,
            @Param("courseStatus") CourseStatus courseStatus,
            @Param("lessonStatus") LessonStatus lessonStatus
    );

    @Query("""
            select lesson
            from Lesson lesson
            join fetch lesson.course course
            join fetch lesson.section section
            where course.id = :courseId
              and lesson.id = :lessonId
              and course.status = :courseStatus
              and course.deletedAt is null
              and lesson.status = :lessonStatus
              and lesson.preview = true
            """)
    Optional<Lesson> findPreviewLesson(
            @Param("courseId") UUID courseId,
            @Param("lessonId") UUID lessonId,
            @Param("courseStatus") CourseStatus courseStatus,
            @Param("lessonStatus") LessonStatus lessonStatus
    );
}
