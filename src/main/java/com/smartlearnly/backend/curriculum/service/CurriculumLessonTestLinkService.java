package com.smartlearnly.backend.curriculum.service;

import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumSection;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.test.definition.dto.TestModel;
import com.smartlearnly.backend.test.definition.service.TestService;
import com.smartlearnly.backend.test.entity.TestType;
import com.smartlearnly.backend.test.repository.TestRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CurriculumLessonTestLinkService {
    private final CurriculumLessonRepository lessonRepository;
    private final TestService testService;
    private final TestRepository testRepository;

    @Transactional
    public UUID ensureQuizTest(CurriculumLesson lesson) {
        if (lesson == null || lesson.getType() != LessonType.QUIZ) {
            return null;
        }
        if (lesson.getTestId() != null) {
            synchronizeExistingQuizTest(lesson);
            return lesson.getTestId();
        }

        CurriculumSection section = lesson.getSection();
        CurriculumVersion version = section == null ? null : section.getCurriculumVersion();

        TestModel.CreateRequest request = new TestModel.CreateRequest();
        request.setCourseId(version == null ? null : version.getCourseId());
        request.setClassId(version == null ? null : version.getClassId());
        request.setModuleId(section == null ? null : section.getSourceModuleId());
        request.setCurriculumSectionId(section == null ? null : section.getId());
        request.setTitle(defaultTestTitle(lesson));
        request.setDescription(null);
        request.setTestType(TestType.practice);
        request.setDurationMinutes(durationMinutes(lesson.getDurationSeconds()));
        request.setMaxAttempts(null);
        request.setShuffleQuestions(false);
        request.setShuffleAnswers(false);
        request.setShowAnswersAfter(true);
        request.setIsPublished(lesson.getStatus() != null && "PUBLISHED".equals(lesson.getStatus().name()));

        TestModel.Response created = testService.createTest(request);
        lesson.setTestId(created.getId());
        lessonRepository.save(lesson);
        return created.getId();
    }

    /** Giữ trạng thái test nội bộ đồng bộ khi quiz lesson chuyển giữa draft và published. */
    private void synchronizeExistingQuizTest(CurriculumLesson lesson) {
        testRepository.findById(lesson.getTestId()).ifPresent(test -> {
            boolean shouldPublish = lesson.getStatus() != null
                    && "PUBLISHED".equals(lesson.getStatus().name());
            String expectedTitle = defaultTestTitle(lesson);
            Integer expectedDurationMinutes = durationMinutes(lesson.getDurationSeconds());

            boolean changed = !java.util.Objects.equals(test.getIsPublished(), shouldPublish)
                    || !java.util.Objects.equals(test.getTitle(), expectedTitle)
                    || !java.util.Objects.equals(test.getDurationMinutes(), expectedDurationMinutes);
            if (!changed) {
                return;
            }

            test.setIsPublished(shouldPublish);
            test.setTitle(expectedTitle);
            test.setDurationMinutes(expectedDurationMinutes);
            testRepository.save(test);
        });
    }

    private Integer durationMinutes(Integer durationSeconds) {
        if (durationSeconds == null || durationSeconds <= 0) {
            return null;
        }
        return Math.max(1, (int) Math.ceil(durationSeconds / 60.0));
    }

    private String defaultTestTitle(CurriculumLesson lesson) {
        String base = lesson.getTitle();
        if (base == null || base.isBlank()) {
            return "Quiz";
        }
        String trimmed = base.trim();
        return trimmed.length() > 240 ? trimmed.substring(0, 240) : trimmed;
    }
}
