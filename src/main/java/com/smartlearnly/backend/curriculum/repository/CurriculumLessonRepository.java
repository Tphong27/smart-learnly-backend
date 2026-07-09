package com.smartlearnly.backend.curriculum.repository;

import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CurriculumLessonRepository extends JpaRepository<CurriculumLesson, UUID> {
    List<CurriculumLesson> findBySectionIdOrderBySortOrderAscCreatedAtAsc(UUID sectionId);

    Optional<CurriculumLesson> findByIdAndSectionId(UUID id, UUID sectionId);

    Optional<CurriculumLesson> findByCurriculumVersionIdAndLessonIdentityId(UUID curriculumVersionId, UUID lessonIdentityId);

    @Query("""
            select lesson
            from CurriculumLesson lesson
            join fetch lesson.section section
            where lesson.curriculumVersionId = :curriculumVersionId
              and (
                  lesson.id = :lessonReferenceId
                  or lesson.sourceLessonId = :lessonReferenceId
                  or lesson.lessonIdentityId = :lessonReferenceId
              )
            """)
    Optional<CurriculumLesson> findEffectiveLessonReference(
            @Param("curriculumVersionId") UUID curriculumVersionId,
            @Param("lessonReferenceId") UUID lessonReferenceId);

    @Query("select coalesce(max(lesson.sortOrder), -1) from CurriculumLesson lesson "
            + "where lesson.section.id = :sectionId")
    int findMaxSortOrderBySectionId(@Param("sectionId") UUID sectionId);
}
