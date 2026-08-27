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
                  or (
                      assignment.classId is null
                      and exists (
                          select managedClass.id
                          from ClassOffering managedClass
                          where managedClass.courseId = curriculumVersion.courseId
                            and managedClass.trainerId = :createdBy
                            and managedClass.deletedAt is null
                      )
                  )
              )
              and (assignment.isFlashtest = false or assignment.isFlashtest is null)
              and assignment.isArchived = false
            order by assignment.createdAt desc
            """)
    List<Assignment> findStaffAssignments(
            @Param("createdBy") UUID createdBy,
            @Param("courseId") UUID courseId);

    /** Kiểm tra nhân sự có sở hữu nội dung hoặc được phân công lớp của assignment. */
    @Query("""
            select count(assignment) > 0
            from Assignment assignment
            left join ClassOffering classOffering on classOffering.id = assignment.classId
            left join CurriculumLesson curriculumLesson on curriculumLesson.id = assignment.lessonId
            left join CurriculumVersion curriculumVersion on curriculumVersion.id = curriculumLesson.curriculumVersionId
            where assignment.id = :assignmentId
              and (assignment.isFlashtest = false or assignment.isFlashtest is null)
              and (
                  assignment.createdBy = :staffId
                  or classOffering.trainerId = :staffId
                  or curriculumVersion.createdBy = :staffId
                  or (
                      assignment.classId is null
                      and exists (
                          select managedClass.id
                          from ClassOffering managedClass
                          where managedClass.courseId = curriculumVersion.courseId
                            and managedClass.trainerId = :staffId
                            and managedClass.deletedAt is null
                      )
                  )
              )
            """)
    boolean existsManagedByStaff(
            @Param("assignmentId") UUID assignmentId,
            @Param("staffId") UUID staffId);

    @Query("""
            select distinct assignment
            from Assignment assignment
            left join CurriculumLesson assignmentLesson
                on assignmentLesson.id = assignment.lessonId
            left join CurriculumVersion assignmentVersion
                on assignmentVersion.id = assignmentLesson.curriculumVersionId
            join ClassEnrollment classEnrollment
                on classEnrollment.studentId = :studentId
            join ClassOffering enrolledClass
                on enrolledClass.id = classEnrollment.classId
            where classEnrollment.studentId = :studentId
              and classEnrollment.status in (
                  com.smartlearnly.backend.enrollment.entity.EnrollmentStatus.ACTIVE,
                  com.smartlearnly.backend.enrollment.entity.EnrollmentStatus.COMPLETED
              )
              and enrolledClass.deletedAt is null
              and (:courseId is null or enrolledClass.courseId = :courseId)
              and (:classId is null or enrolledClass.id = :classId)
              and (
                  assignment.classId = enrolledClass.id
                  or (
                      assignment.classId is null
                      and assignmentVersion.courseId = enrolledClass.courseId
                      and (
                          assignmentVersion.classId is null
                          or assignmentVersion.classId = enrolledClass.id
                      )
                      and exists (
                          select availableLesson.id
                          from CurriculumLesson availableLesson
                          join CurriculumVersion availableVersion
                              on availableVersion.id = availableLesson.curriculumVersionId
                          where availableVersion.courseId = enrolledClass.courseId
                            and (
                                availableVersion.classId is null
                                or availableVersion.classId = enrolledClass.id
                            )
                            and availableVersion.status = com.smartlearnly.backend.curriculum.entity.CurriculumStatus.PUBLISHED
                            and availableLesson.status = com.smartlearnly.backend.learning.lesson.entity.LessonStatus.PUBLISHED
                            and availableLesson.type in (
                                com.smartlearnly.backend.learning.lesson.entity.LessonType.ASSIGNMENT,
                                com.smartlearnly.backend.learning.lesson.entity.LessonType.ESSAY
                            )
                            and (
                                availableLesson.id = assignment.lessonId
                                or availableLesson.lessonIdentityId = assignmentLesson.lessonIdentityId
                                or availableLesson.sourceCurriculumLessonId = assignment.lessonId
                                or availableLesson.sourceLessonId = assignment.lessonId
                            )
                      )
                  )
              )
              and (assignment.isFlashtest = false or assignment.isFlashtest is null)
              and assignment.isArchived = false
            order by assignment.createdAt desc
            """)
    List<Assignment> findAvailableForStudent(
            @Param("studentId") UUID studentId,
            @Param("courseId") UUID courseId,
            @Param("classId") UUID classId);

    /** Kiểm tra học viên được làm assignment của lớp hoặc essay kế thừa từ course đã publish. */
    @Query("""
            select count(assignment) > 0
            from Assignment assignment
            left join CurriculumLesson assignmentLesson
                on assignmentLesson.id = assignment.lessonId
            left join CurriculumVersion assignmentVersion
                on assignmentVersion.id = assignmentLesson.curriculumVersionId
            join ClassEnrollment classEnrollment
                on classEnrollment.studentId = :studentId
            join ClassOffering enrolledClass
                on enrolledClass.id = classEnrollment.classId
            where assignment.id = :assignmentId
              and classEnrollment.studentId = :studentId
              and classEnrollment.status in (
                  com.smartlearnly.backend.enrollment.entity.EnrollmentStatus.ACTIVE,
                  com.smartlearnly.backend.enrollment.entity.EnrollmentStatus.COMPLETED
              )
              and enrolledClass.deletedAt is null
              and (
                  assignment.classId = enrolledClass.id
                  or (
                      assignment.classId is null
                      and assignmentVersion.courseId = enrolledClass.courseId
                      and (
                          assignmentVersion.classId is null
                          or assignmentVersion.classId = enrolledClass.id
                      )
                      and exists (
                          select availableLesson.id
                          from CurriculumLesson availableLesson
                          join CurriculumVersion availableVersion
                              on availableVersion.id = availableLesson.curriculumVersionId
                          where availableVersion.courseId = enrolledClass.courseId
                            and (
                                availableVersion.classId is null
                                or availableVersion.classId = enrolledClass.id
                            )
                            and availableVersion.status = com.smartlearnly.backend.curriculum.entity.CurriculumStatus.PUBLISHED
                            and availableLesson.status = com.smartlearnly.backend.learning.lesson.entity.LessonStatus.PUBLISHED
                            and availableLesson.type in (
                                com.smartlearnly.backend.learning.lesson.entity.LessonType.ASSIGNMENT,
                                com.smartlearnly.backend.learning.lesson.entity.LessonType.ESSAY
                            )
                            and (
                                availableLesson.id = assignment.lessonId
                                or availableLesson.lessonIdentityId = assignmentLesson.lessonIdentityId
                                or availableLesson.sourceCurriculumLessonId = assignment.lessonId
                                or availableLesson.sourceLessonId = assignment.lessonId
                            )
                      )
                  )
              )
              and assignment.isArchived = false
              and (assignment.isFlashtest = false or assignment.isFlashtest is null)
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
