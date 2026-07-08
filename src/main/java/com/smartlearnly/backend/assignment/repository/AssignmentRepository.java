package com.smartlearnly.backend.assignment.repository;

import com.smartlearnly.backend.assignment.entity.Assignment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

    List<Assignment> findByClassId(UUID classId);

    @Query("""
            select assignment
            from Assignment assignment
            join ClassOffering classOffering on classOffering.id = assignment.classId
            where (:courseId is null or classOffering.courseId = :courseId)
              and (
                  :createdBy is null
                  or assignment.createdBy = :createdBy
                  or classOffering.trainerId = :createdBy
              )
              and (
                  :isFlashtest is null
                  or (:isFlashtest = true and assignment.isFlashtest = true)
                  or (:isFlashtest = false and (assignment.isFlashtest = false or assignment.isFlashtest is null))
              )
              and assignment.isArchived = false
            order by assignment.createdAt desc
            """)
    List<Assignment> findStaffAssignments(
            @Param("createdBy") UUID createdBy,
            @Param("courseId") UUID courseId,
            @Param("isFlashtest") Boolean isFlashtest);

    @Query("""
            select assignment
            from Assignment assignment
            join ClassOffering classOffering on classOffering.id = assignment.classId
            join ClassEnrollment classEnrollment on classEnrollment.classId = assignment.classId
            where classEnrollment.studentId = :studentId
              and classEnrollment.status = com.smartlearnly.backend.enrollment.entity.EnrollmentStatus.ACTIVE
              and (:courseId is null or classOffering.courseId = :courseId)
              and (
                  :isFlashtest is null
                  or (:isFlashtest = true and assignment.isFlashtest = true)
                  or (:isFlashtest = false and (assignment.isFlashtest = false or assignment.isFlashtest is null))
              )
              and assignment.isArchived = false
            order by assignment.createdAt desc
            """)
    List<Assignment> findAvailableForStudent(
            @Param("studentId") UUID studentId,
            @Param("courseId") UUID courseId,
            @Param("isFlashtest") Boolean isFlashtest);

    Optional<Assignment> findByLessonId(UUID lessonId);

}
