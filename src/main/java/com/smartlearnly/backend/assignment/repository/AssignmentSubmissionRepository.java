package com.smartlearnly.backend.assignment.repository;

import com.smartlearnly.backend.assignment.entity.AssignmentSubmission;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssignmentSubmissionRepository
        extends JpaRepository<AssignmentSubmission, UUID> {

    List<AssignmentSubmission> findByAssignmentId(UUID assignmentId);

    List<AssignmentSubmission> findByStudentId(UUID studentId);

    Optional<AssignmentSubmission> findByAssignmentIdAndStudentId(
            UUID assignmentId,
            UUID studentId);

    @Query("""
            select count(submission) > 0
            from AssignmentSubmission submission
            where submission.assignmentId = :assignmentId
              and submission.status = com.smartlearnly.backend.assignment.entity.SubmissionStatus.DOING
            """)
    boolean existsActiveByAssignmentId(@Param("assignmentId") UUID assignmentId);

    /** Đếm submission đã hoàn thành trong một nhóm assignment mà không phát sinh N+1 query. */
    @Query("""
            select count(submission)
            from AssignmentSubmission submission
            where submission.studentId = :studentId
              and submission.assignmentId in :assignmentIds
              and submission.status in (
                  com.smartlearnly.backend.assignment.entity.SubmissionStatus.SUBMITTED,
                  com.smartlearnly.backend.assignment.entity.SubmissionStatus.GRADED,
                  com.smartlearnly.backend.assignment.entity.SubmissionStatus.EXPIRED
              )
            """)
    long countCompletedByStudentIdAndAssignmentIds(
            @Param("studentId") UUID studentId,
            @Param("assignmentIds") List<UUID> assignmentIds);

    /** Tìm submission đang tham chiếu file đã lưu để kiểm tra quyền download. */
    Optional<AssignmentSubmission> findByFileUrl(String fileUrl);

    void deleteByAssignmentId(UUID assignmentId);

}
