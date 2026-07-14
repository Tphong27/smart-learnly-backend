package com.smartlearnly.backend.curriculum.repository;

import com.smartlearnly.backend.curriculum.entity.CurriculumLessonResource;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CurriculumLessonResourceRepository extends JpaRepository<CurriculumLessonResource, UUID> {
    List<CurriculumLessonResource> findByLessonIdOrderBySortOrderAscCreatedAtAsc(UUID lessonId);
}
