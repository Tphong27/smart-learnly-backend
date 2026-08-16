
package com.smartlearnly.backend.test.repository;

import com.smartlearnly.backend.test.entity.Test;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TestRepository
        extends JpaRepository<Test, UUID> {

    @Query("""
            select test
            from Test test
            left join ClassOffering classOffering
                on classOffering.id = test.classId
            where (test.isFlashtest = false or test.isFlashtest is null)
              and test.isArchived = false
              and (:courseId is null or test.courseId = :courseId)
              and (:classId is null or test.classId = :classId)
              and (
                  :privileged = true
                  or test.createdBy = :staffId
                  or classOffering.trainerId = :staffId
              )
            order by test.createdAt desc
            """)
    List<Test> findManagedTests(
            @Param("staffId") UUID staffId,
            @Param("courseId") UUID courseId,
            @Param("classId") UUID classId,
            @Param("privileged") boolean privileged);

    @Query("""
            select distinct test
            from Test test
            left join ClassEnrollment classEnrollment
                on classEnrollment.classId = test.classId
               and classEnrollment.studentId = :studentId
            left join CourseEnrollment courseEnrollment
                on courseEnrollment.courseId = test.courseId
               and courseEnrollment.studentId = :studentId
            where (test.isFlashtest = false or test.isFlashtest is null)
              and test.isArchived = false
              and test.isPublished = true
              and (:courseId is null or test.courseId = :courseId)
              and (:classId is null or test.classId = :classId)
              and (
                  classEnrollment.status in (
                      com.smartlearnly.backend.enrollment.entity.EnrollmentStatus.ACTIVE,
                      com.smartlearnly.backend.enrollment.entity.EnrollmentStatus.COMPLETED
                  )
                  or (
                      test.classId is null
                      and courseEnrollment.status in (
                          com.smartlearnly.backend.enrollment.entity.EnrollmentStatus.ACTIVE,
                          com.smartlearnly.backend.enrollment.entity.EnrollmentStatus.COMPLETED
                      )
                  )
              )
            order by test.createdAt desc
            """)
    List<Test> findAvailableTestsForStudent(
            @Param("studentId") UUID studentId,
            @Param("courseId") UUID courseId,
            @Param("classId") UUID classId);

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
              and (test.isFlashtest = false or test.isFlashtest is null)
            """)
    boolean existsAvailableForStudent(
            @Param("testId") UUID testId,
            @Param("studentId") UUID studentId);

    @Query("""
            select case when count(test) > 0 then true else false end
            from Test test
            join CourseEnrollment courseEnrollment
                on courseEnrollment.courseId = test.courseId
               and courseEnrollment.studentId = :studentId
               and courseEnrollment.status in (
                  com.smartlearnly.backend.enrollment.entity.EnrollmentStatus.ACTIVE,
                  com.smartlearnly.backend.enrollment.entity.EnrollmentStatus.COMPLETED
               )
            where test.id = :testId
              and test.classId is null
              and (
                  test.isPublished = true
                  or exists (
                      select lesson.id
                      from CurriculumLesson lesson
                      join CurriculumVersion version
                          on version.id = lesson.curriculumVersionId
                      where lesson.testId = test.id
                        and lesson.type = com.smartlearnly.backend.learning.lesson.entity.LessonType.QUIZ
                        and lesson.status = com.smartlearnly.backend.learning.lesson.entity.LessonStatus.PUBLISHED
                        and version.courseId = test.courseId
                        and version.classId is null
                        and version.scope = com.smartlearnly.backend.curriculum.entity.CurriculumScope.MASTER
                        and version.status = com.smartlearnly.backend.curriculum.entity.CurriculumStatus.PUBLISHED
                  )
              )
              and test.isArchived = false
              and (test.isFlashtest = false or test.isFlashtest is null)
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
              and test.classId is null
              and classEnrollment.studentId = :studentId
              and classEnrollment.status in (
                  com.smartlearnly.backend.enrollment.entity.EnrollmentStatus.ACTIVE,
                  com.smartlearnly.backend.enrollment.entity.EnrollmentStatus.COMPLETED
              )
              and (
                  test.isPublished = true
                  or exists (
                      select lesson.id
                      from CurriculumLesson lesson
                      join CurriculumVersion version
                          on version.id = lesson.curriculumVersionId
                      where lesson.testId = test.id
                        and lesson.type = com.smartlearnly.backend.learning.lesson.entity.LessonType.QUIZ
                        and lesson.status = com.smartlearnly.backend.learning.lesson.entity.LessonStatus.PUBLISHED
                        and version.courseId = test.courseId
                        and version.classId is null
                        and version.scope = com.smartlearnly.backend.curriculum.entity.CurriculumScope.MASTER
                        and version.status = com.smartlearnly.backend.curriculum.entity.CurriculumStatus.PUBLISHED
                  )
              )
              and test.isArchived = false
              and (test.isFlashtest = false or test.isFlashtest is null)
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
              and (test.isFlashtest = false or test.isFlashtest is null)
              and (
                  test.createdBy = :staffId
                  or classOffering.trainerId = :staffId
              )
            """)
    boolean existsManagedByStaff(
            @Param("testId") UUID testId,
            @Param("staffId") UUID staffId);
}

