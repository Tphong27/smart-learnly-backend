package com.smartlearnly.backend.curriculum.repository;

import com.smartlearnly.backend.curriculum.entity.CurriculumScope;
import com.smartlearnly.backend.curriculum.entity.CurriculumStatus;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CurriculumVersionRepository extends JpaRepository<CurriculumVersion, UUID> {
    Optional<CurriculumVersion> findFirstByCourseIdAndScopeAndStatusOrderByVersionNumberDescCreatedAtDesc(
            UUID courseId,
            CurriculumScope scope,
            CurriculumStatus status);

    Optional<CurriculumVersion> findFirstByCourseIdAndScopeOrderByVersionNumberDescCreatedAtDesc(
            UUID courseId,
            CurriculumScope scope);

    Optional<CurriculumVersion> findByIdAndCourseId(UUID id, UUID courseId);

    Optional<CurriculumVersion> findByIdAndClassId(UUID id, UUID classId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select version from CurriculumVersion version where version.id = :id")
    Optional<CurriculumVersion> findByIdForUpdate(@Param("id") UUID id);

    @Query("select coalesce(max(version.versionNumber), 0) from CurriculumVersion version "
            + "where version.courseId = :courseId and version.scope = :scope")
    int findMaxMasterVersionNumber(
            @Param("courseId") UUID courseId,
            @Param("scope") CurriculumScope scope);

    @Query("select coalesce(max(version.versionNumber), 0) from CurriculumVersion version "
            + "where version.classId = :classId and version.scope = :scope")
    int findMaxClassVersionNumber(
            @Param("classId") UUID classId,
            @Param("scope") CurriculumScope scope);
}
