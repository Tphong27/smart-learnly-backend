package com.smartlearnly.backend.curriculum.repository;

import com.smartlearnly.backend.curriculum.entity.ClassCurriculumEntry;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassCurriculumEntryRepository extends JpaRepository<ClassCurriculumEntry, UUID> {

    List<ClassCurriculumEntry> findByClassVersionIdOrderBySortOrderAscCreatedAtAsc(UUID classVersionId);

    List<ClassCurriculumEntry> findByClassVersionIdAndSectionIdOrderBySortOrderAsc(UUID classVersionId, UUID sectionId);

    Optional<ClassCurriculumEntry> findByIdAndClassVersionId(UUID id, UUID classVersionId);

    /**
     * Locked variant used by materialize-on-write so two concurrent writers cannot both
     * materialize the same inherited entry.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select entry from ClassCurriculumEntry entry "
            + "where entry.id = :id and entry.classVersionId = :classVersionId")
    Optional<ClassCurriculumEntry> findByIdAndClassVersionIdForUpdate(
            @Param("id") UUID id,
            @Param("classVersionId") UUID classVersionId);

    Optional<ClassCurriculumEntry> findByClassVersionIdAndSourceCurriculumLessonId(
            UUID classVersionId, UUID sourceCurriculumLessonId);

    Optional<ClassCurriculumEntry> findByClassVersionIdAndLessonIdentityId(
            UUID classVersionId, UUID lessonIdentityId);

    Optional<ClassCurriculumEntry> findByMaterializedLessonId(UUID materializedLessonId);

    boolean existsByClassVersionId(UUID classVersionId);

    @Query("select coalesce(max(entry.sortOrder), -1) from ClassCurriculumEntry entry "
            + "where entry.classVersionId = :classVersionId and entry.section.id = :sectionId")
    int findMaxSortOrderByClassVersionIdAndSectionId(
            @Param("classVersionId") UUID classVersionId,
            @Param("sectionId") UUID sectionId);
}
