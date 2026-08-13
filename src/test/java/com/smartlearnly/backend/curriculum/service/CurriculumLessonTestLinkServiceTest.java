package com.smartlearnly.backend.curriculum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.test.definition.service.TestService;
import com.smartlearnly.backend.test.repository.TestRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CurriculumLessonTestLinkServiceTest {

    @Mock
    private CurriculumLessonRepository lessonRepository;
    @Mock
    private TestService testService;
    @Mock
    private TestRepository testRepository;

    @InjectMocks
    private CurriculumLessonTestLinkService service;

    @Test
    void existingQuizTestShouldFollowPublishedLessonState() {
        UUID testId = UUID.randomUUID();
        CurriculumLesson lesson = new CurriculumLesson();
        lesson.setType(LessonType.QUIZ);
        lesson.setStatus(LessonStatus.PUBLISHED);
        lesson.setTitle("React hooks quiz");
        lesson.setDurationSeconds(125);
        lesson.setTestId(testId);

        com.smartlearnly.backend.test.entity.Test linkedTest =
                new com.smartlearnly.backend.test.entity.Test();
        linkedTest.setId(testId);
        linkedTest.setTitle("Old title");
        linkedTest.setDurationMinutes(null);
        linkedTest.setIsPublished(false);
        when(testRepository.findById(testId)).thenReturn(Optional.of(linkedTest));

        UUID result = service.ensureQuizTest(lesson);

        assertThat(result).isEqualTo(testId);
        assertThat(linkedTest.getIsPublished()).isTrue();
        assertThat(linkedTest.getTitle()).isEqualTo("React hooks quiz");
        assertThat(linkedTest.getDurationMinutes()).isEqualTo(3);
        verify(testRepository).save(linkedTest);
        verifyNoInteractions(testService, lessonRepository);
    }
}
