
package com.smartlearnly.backend.test.repository;

import com.smartlearnly.backend.test.entity.Test;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TestRepository
        extends JpaRepository<Test, UUID> {

    List<Test> findByCourseId(UUID courseId);

    List<Test> findByClassId(UUID classId);

    List<Test> findByModuleId(UUID moduleId);

    List<Test> findByCreatedBy(UUID createdBy);

    List<Test> findByCreatedByAndCourseId(UUID createdBy, UUID courseId);

    List<Test> findByIsPublishedTrueAndIsArchivedFalse();

    @Query("""
            select test
            from Test test
            left join ClassOffering classOffering
                on classOffering.id = test.classId
            where (:courseId is null or test.courseId = :courseId)
              and (:classId is null or test.classId = :classId)
              and (
                  :createdBy is null
                  or test.createdBy = :createdBy
                  or classOffering.trainerId = :createdBy
              )
            order by test.createdAt desc
            """)
    List<Test> findStaffTests(
            @Param("createdBy") UUID createdBy,
            @Param("courseId") UUID courseId,
            @Param("classId") UUID classId);

    @Query("""
            select distinct test
            from Test test
            join ClassEnrollment enrollment
                on enrollment.classId = test.classId
            where enrollment.studentId = :studentId
              and enrollment.status in (
                  com.smartlearnly.backend.enrollment.entity.EnrollmentStatus.ACTIVE,
                  com.smartlearnly.backend.enrollment.entity.EnrollmentStatus.COMPLETED
              )
              and test.isPublished = true
              and test.isArchived = false
              and (:courseId is null or test.courseId = :courseId)
              and (:classId is null or test.classId = :classId)
              and (
                  :isFlashtest is null
                  or test.isFlashtest = :isFlashtest
              )
            order by test.createdAt desc
            """)
    List<Test> findAvailableForStudent(
            @Param("studentId") UUID studentId,
            @Param("courseId") UUID courseId,
            @Param("classId") UUID classId,
            @Param("isFlashtest") Boolean isFlashtest);

    @Query("""
            select case when count(test) > 0 then true else false end
            from Test test
            join ClassEnrollment enrollment
                on enrollment.classId = test.classId
            where test.id = :testId
              and enrollment.studentId = :studentId
              and enrollment.status in (
                  com.smartlearnly.backend.enrollment.entity.EnrollmentStatus.ACTIVE,
                  com.smartlearnly.backend.enrollment.entity.EnrollmentStatus.COMPLETED
              )
              and test.isPublished = true
              and test.isArchived = false
            """)
    boolean existsAvailableForStudent(
            @Param("testId") UUID testId,
            @Param("studentId") UUID studentId);

    @Query("""
            select case when count(test) > 0 then true else false end
            from Test test
            left join CourseEnrollment courseEnrollment
                on courseEnrollment.courseId = test.courseId
               and courseEnrollment.studentId = :studentId
               and courseEnrollment.status in (
                  com.smartlearnly.backend.enrollment.entity.EnrollmentStatus.ACTIVE,
                  com.smartlearnly.backend.enrollment.entity.EnrollmentStatus.COMPLETED
               )
            left join ClassOffering classOffering
                on classOffering.courseId = test.courseId
            left join ClassEnrollment classEnrollment
                on classEnrollment.classId = classOffering.id
               and classEnrollment.studentId = :studentId
               and classEnrollment.status in (
                  com.smartlearnly.backend.enrollment.entity.EnrollmentStatus.ACTIVE,
                  com.smartlearnly.backend.enrollment.entity.EnrollmentStatus.COMPLETED
               )
            where test.id = :testId
              and test.classId is null
              and (courseEnrollment.id is not null or classEnrollment.id is not null)
              and test.isPublished = true
              and test.isArchived = false
            """)
    boolean existsAvailableCourseTestForStudent(
            @Param("testId") UUID testId,
            @Param("studentId") UUID studentId);

    @Query("""
            select case when count(test) > 0 then true else false end
            from Test test
            join ClassOffering classOffering
                on classOffering.id = :classId
               and classOffering.courseId = test.courseId
            join ClassEnrollment classEnrollment
                on classEnrollment.classId = classOffering.id
            where test.id = :testId
              and classEnrollment.studentId = :studentId
              and classEnrollment.status in (
                  com.smartlearnly.backend.enrollment.entity.EnrollmentStatus.ACTIVE,
                  com.smartlearnly.backend.enrollment.entity.EnrollmentStatus.COMPLETED
              )
              and test.isArchived = false
            """)
    boolean existsAvailableCourseTestForStudentClass(
            @Param("testId") UUID testId,
            @Param("studentId") UUID studentId,
            @Param("classId") UUID classId);

    @Query("""
            select case when count(test) > 0 then true else false end
            from Test test
            left join ClassOffering classOffering
                on classOffering.id = test.classId
            where test.id = :testId
              and (
                  test.createdBy = :staffId
                  or classOffering.trainerId = :staffId
              )
            """)
    boolean existsManagedByStaff(
            @Param("testId") UUID testId,
            @Param("staffId") UUID staffId);
}

