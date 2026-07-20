package com.smartlearnly.backend.hls.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.course.service.CourseAccessService;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumScope;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.curriculum.service.TrainerClassCurriculumService;
import com.smartlearnly.backend.learning.lesson.entity.Lesson;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.learning.lesson.repository.LessonRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HlsLessonAccessService {
    private final LessonRepository lessonRepository;
    private final CurriculumLessonRepository curriculumLessonRepository;
    private final CourseAccessService courseAccessService;
    private final TrainerClassCurriculumService trainerClassCurriculumService;

    @Transactional(readOnly = true)
    public void requireReadable(UUID lessonId) {
        requireAccess(lessonId, false, false);
    }

    @Transactional(readOnly = true)
    public void requireWritable(UUID lessonId) {
        requireAccess(lessonId, true, false);
    }

    @Transactional(readOnly = true)
    public void requireWritableVideo(UUID lessonId) {
        requireAccess(lessonId, true, true);
    }

    private void requireAccess(UUID lessonId, boolean write, boolean requireVideo) {
        Lesson lesson = lessonRepository.findById(lessonId).orElse(null);
        if (lesson != null) {
            if (requireVideo && lesson.getType() != LessonType.VIDEO) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "HLS processing requires a video lesson");
            }
            if (write) {
                courseAccessService.requireUpdatableCourse(lesson.getCourse().getId());
            } else {
                courseAccessService.requireReadableCourse(lesson.getCourse().getId());
            }
            return;
        }

        CurriculumLesson curriculumLesson = curriculumLessonRepository.findById(lessonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Lesson not found: " + lessonId));
        if (requireVideo && curriculumLesson.getType() != LessonType.VIDEO) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "HLS processing requires a video lesson");
        }

        var version = curriculumLesson.getSection().getCurriculumVersion();
        if (version.getScope() == CurriculumScope.CLASS) {
            if (version.getClassId() == null) {
                throw new BusinessException(ErrorCode.CONFLICT, "Class curriculum is inconsistent");
            }
            if (write) {
                trainerClassCurriculumService.requireOwnedClassLessonForWrite(version.getClassId(), lessonId);
            } else {
                trainerClassCurriculumService.requireOwnedClassLessonForRead(version.getClassId(), lessonId);
            }
            return;
        }

        if (write) {
            courseAccessService.requireUpdatableCourse(version.getCourseId());
        } else {
            courseAccessService.requireReadableCourse(version.getCourseId());
        }
    }
}
