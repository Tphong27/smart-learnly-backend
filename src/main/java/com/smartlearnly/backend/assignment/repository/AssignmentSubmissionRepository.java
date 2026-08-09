package com.smartlearnly.backend.assignment.repository;

import com.smartlearnly.backend.assignment.entity.AssignmentSubmission;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignmentSubmissionRepository
        extends JpaRepository<AssignmentSubmission, UUID> {

    List<AssignmentSubmission> findByAssignmentId(UUID assignmentId);

    List<AssignmentSubmission> findByStudentId(UUID studentId);

    Optional<AssignmentSubmission> findByAssignmentIdAndStudentId(
            UUID assignmentId,
            UUID studentId);

    /** Tìm submission đang tham chiếu file đã lưu để kiểm tra quyền download. */
    Optional<AssignmentSubmission> findByFileUrl(String fileUrl);

    void deleteByAssignmentId(UUID assignmentId);

}
