package com.smartlearnly.backend.curriculum.repository;

import com.smartlearnly.backend.curriculum.entity.CurriculumSection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CurriculumSectionRepository extends JpaRepository<CurriculumSection, UUID> {
    List<CurriculumSection> findByCurriculumVersionIdOrderBySortOrderAscCreatedAtAsc(UUID curriculumVersionId);

    Optional<CurriculumSection> findByIdAndCurriculumVersionId(UUID id, UUID curriculumVersionId);

    @Query("select coalesce(max(section.sortOrder), -1) from CurriculumSection section "
            + "where section.curriculumVersion.id = :curriculumVersionId")
    int findMaxSortOrderByCurriculumVersionId(@Param("curriculumVersionId") UUID curriculumVersionId);
}
