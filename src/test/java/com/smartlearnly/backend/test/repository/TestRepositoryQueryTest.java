package com.smartlearnly.backend.test.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

class TestRepositoryQueryTest {

    @Test
    void directCourseAccessShouldAcceptPublishedMasterCurriculumQuiz() throws Exception {
        Query query = queryFor(
                "existsAvailableCourseTestForStudent",
                UUID.class,
                UUID.class);

        assertThat(query.value())
                .contains("test.classId is null")
                .contains("from CurriculumLesson lesson")
                .contains("lesson.testId = test.id")
                .contains("LessonType.QUIZ")
                .contains("LessonStatus.PUBLISHED")
                .contains("CurriculumScope.MASTER")
                .contains("CurriculumStatus.PUBLISHED");
    }

    @Test
    void classContextShouldOnlyGrantAccessToMasterCourseTests() throws Exception {
        Query query = queryFor(
                "existsAvailableCourseTestForStudentClass",
                UUID.class,
                UUID.class,
                UUID.class);

        assertThat(query.value())
                .contains("classOffering.id = :classId")
                .contains("classOffering.courseId = test.courseId")
                .contains("test.classId is null")
                .contains("from CurriculumLesson lesson")
                .contains("CurriculumScope.MASTER")
                .contains("CurriculumStatus.PUBLISHED");
    }

    private Query queryFor(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = TestRepository.class.getMethod(methodName, parameterTypes);
        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();
        return query;
    }
}
