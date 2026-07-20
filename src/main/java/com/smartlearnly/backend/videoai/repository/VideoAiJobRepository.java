package com.smartlearnly.backend.videoai.repository;

import com.smartlearnly.backend.videoai.entity.VideoAiJob;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

public interface VideoAiJobRepository extends JpaRepository<VideoAiJob, UUID> {
    @Query("select job from VideoAiJob job where job.lessonId = :lessonId and job.lessonScope = :scope "
            + "and ((:classId is null and job.classId is null) or job.classId = :classId) "
            + "order by job.createdAt desc")
    List<VideoAiJob> findLatestForLesson(
            @Param("lessonId") UUID lessonId,
            @Param("scope") String scope,
            @Param("classId") UUID classId,
            Pageable pageable);

    @Query("select job from VideoAiJob job where job.lessonId = :lessonId and job.lessonScope = :scope "
            + "and ((:classId is null and job.classId is null) or job.classId = :classId) "
            + "and job.sourceVersion = :sourceVersion and job.jobType = :jobType order by job.createdAt desc")
    List<VideoAiJob> findLatestForSource(
            @Param("lessonId") UUID lessonId,
            @Param("scope") String scope,
            @Param("classId") UUID classId,
            @Param("sourceVersion") UUID sourceVersion,
            @Param("jobType") String jobType,
            Pageable pageable);

    @Query("select job from VideoAiJob job where job.lessonId = :lessonId and job.lessonScope = :scope "
            + "and ((:classId is null and job.classId is null) or job.classId = :classId) "
            + "and job.sourceVersion = :sourceVersion and job.jobType = :jobType "
            + "and job.status in ('pending', 'processing') order by job.createdAt desc")
    List<VideoAiJob> findActive(
            @Param("lessonId") UUID lessonId,
            @Param("scope") String scope,
            @Param("classId") UUID classId,
            @Param("sourceVersion") UUID sourceVersion,
            @Param("jobType") String jobType,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from VideoAiJob job where (job.status = 'pending' and job.nextAttemptAt <= :now) "
            + "or (job.status = 'processing' and job.leaseExpiresAt < :now) order by job.createdAt asc")
    List<VideoAiJob> findClaimable(@Param("now") Instant now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from VideoAiJob job where job.id = :jobId and job.leaseOwner = :leaseOwner "
            + "and job.status = 'processing'")
    Optional<VideoAiJob> findOwnedForUpdate(
            @Param("jobId") UUID jobId,
            @Param("leaseOwner") UUID leaseOwner);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update VideoAiJob job set job.leaseExpiresAt = :leaseExpiresAt, "
            + "job.leaseHeartbeatAt = :heartbeatAt, job.updatedAt = :heartbeatAt "
            + "where job.id = :jobId and job.leaseOwner = :leaseOwner and job.status = 'processing'")
    int extendLease(
            @Param("jobId") UUID jobId,
            @Param("leaseOwner") UUID leaseOwner,
            @Param("leaseExpiresAt") Instant leaseExpiresAt,
            @Param("heartbeatAt") Instant heartbeatAt);

    Optional<VideoAiJob> findByIdAndLessonId(UUID id, UUID lessonId);
}
