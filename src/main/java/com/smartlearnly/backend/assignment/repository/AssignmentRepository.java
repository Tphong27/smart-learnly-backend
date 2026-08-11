package com.smartlearnly.backend.assignment.repository;

import com.smartlearnly.backend.assignment.entity.Assignment;
import java.util.Collection;
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
            left join ClassOffering classOffering on classOffering.id = assignment.classId
            left join CurriculumLesson curriculumLesson on curriculumLesson.id = assignment.lessonId
            left join CurriculumVersion curriculumVersion on curriculumVersion.id = curriculumLesson.curriculumVersionId
            where (
                  :courseId is null
                  or classOffering.courseId = :courseId
                  or curriculumVersion.courseId = :courseId
              )
              and (
                  :createdBy is null
                  or assignment.createdBy = :createdBy
                  or classOffering.trainerId = :createdBy
                  or curriculumVersion.createdBy = :createdBy
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

    /** Kiểm tra nhân sự có sở hữu nội dung hoặc được phân công lớp của assignment. */
    @Query("""
            select count(assignment) > 0
            from Assignment assignment
            left join ClassOffering classOffering on classOffering.id = assignment.classId
            left join CurriculumLesson curriculumLesson on curriculumLesson.id = assignment.lessonId
            left join CurriculumVersion curriculumVersion on curriculumVersion.id = curriculumLesson.curriculumVersionId
            where assignment.id = :assignmentId
              and (
                  assignment.createdBy = :staffId
                  or classOffering.trainerId = :staffId
                  or curriculumVersion.createdBy = :staffId
              )
            """)
    boolean existsManagedByStaff(
            @Param("assignmentId") UUID assignmentId,
            @Param("staffId") UUID staffId);

    @Query("""
            select assignment
            from Assignment assignment
            join ClassOffering classOffering
                on classOffering.id = assignment.classId
            join ClassEnrollment classEnrollment
                on classEnrollment.classId = assignment.classId
            where classEnrollment.studentId = :studentId
              and classEnrollment.status in (
                  com.smartlearnly.backend.enrollment.entity.EnrollmentStatus.ACTIVE,
                  com.smartlearnly.backend.enrollment.entity.EnrollmentStatus.COMPLETED
              )
              and (:courseId is null or classOffering.courseId = :courseId)
              and (:classId is null or assignment.classId = :classId)
              and (
                  :isFlashtest is null
                  or (:isFlashtest = true and assignment.isFlashtest = true)
                  or (
                      :isFlashtest = false
                      and (
                          assignment.isFlashtest = false
                          or assignment.isFlashtest is null
                      )
                  )
              )
              and assignment.isArchived = false
            order by assignment.createdAt desc
            """)
    List<Assignment> findAvailableForStudent(
            @Param("studentId") UUID studentId,
            @Param("courseId") UUID courseId,
            @Param("classId") UUID classId,
            @Param("isFlashtest") Boolean isFlashtest);

    /** Kiểm tra học viên đang hoặc đã ghi danh đúng lớp của assignment. */
    @Query("""
            select count(assignment) > 0
            from Assignment assignment
            join ClassEnrollment classEnrollment on classEnrollment.classId = assignment.classId
            where assignment.id = :assignmentId
              and classEnrollment.studentId = :studentId
              and classEnrollment.status in (
                  com.smartlearnly.backend.enrollment.entity.EnrollmentStatus.ACTIVE,
                  com.smartlearnly.backend.enrollment.entity.EnrollmentStatus.COMPLETED
              )
              and assignment.isArchived = false
            """)
    boolean existsAvailableForStudent(
            @Param("assignmentId") UUID assignmentId,
            @Param("studentId") UUID studentId);

    Optional<Assignment> findByLessonId(UUID lessonId);

    /** Tìm assignment đang tham chiếu file hướng dẫn đã lưu. */
    Optional<Assignment> findByInstructionFileUrl(String instructionFileUrl);

    List<Assignment> findByLessonIdIn(Collection<UUID> lessonIds);

    List<Assignment> findByLessonIdInAndClassId(
            Collection<UUID> lessonIds,
            UUID classId);

    List<Assignment> findByLessonIdInAndClassIdIsNull(
            Collection<UUID> lessonIds);

}
