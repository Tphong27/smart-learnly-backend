package com.smartlearnly.backend.assignment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

class AssignmentRepositoryQueryTest {

    @Test
    void staffAssignmentsShouldIncludeSharedCourseEssayForAssignedTrainer() throws Exception {
        Query query = queryFor(
                "findStaffAssignments",
                UUID.class,
                UUID.class);

        assertThat(query.value())
                .contains("assignment.classId is null")
                .contains("from ClassOffering managedClass")
                .contains("managedClass.courseId = curriculumVersion.courseId")
                .contains("managedClass.trainerId = :createdBy");
    }

    @Test
    void availableAssignmentsShouldIncludePublishedCourseEssayForEnrolledClass() throws Exception {
        Query query = queryFor(
                "findAvailableForStudent",
                UUID.class,
                UUID.class,
                UUID.class);

        assertThat(query.value())
                .contains("select distinct assignment")
                .contains("assignment.classId = enrolledClass.id")
                .contains("assignment.classId is null")
                .contains("assignmentVersion.courseId = enrolledClass.courseId")
                .contains("availableVersion.status = com.smartlearnly.backend.curriculum.entity.CurriculumStatus.PUBLISHED")
                .contains("availableLesson.lessonIdentityId = assignmentLesson.lessonIdentityId");
    }

    @Test
    void submissionAccessShouldAcceptPublishedCourseEssayForEnrolledClass() throws Exception {
        Query query = queryFor(
                "existsAvailableForStudent",
                UUID.class,
                UUID.class);

        assertThat(query.value())
                .contains("assignment.classId = enrolledClass.id")
                .contains("assignment.classId is null")
                .contains("assignmentVersion.courseId = enrolledClass.courseId")
                .contains("availableLesson.type in")
                .contains("LessonType.ASSIGNMENT")
                .contains("LessonType.ESSAY");
    }

    private Query queryFor(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = AssignmentRepository.class.getMethod(methodName, parameterTypes);
        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();
        return query;
    }
}
