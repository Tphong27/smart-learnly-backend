package com.smartlearnly.backend.curriculum.cloning;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartlearnly.backend.curriculum.entity.*;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CurriculumEntityCopierTest {

    private CurriculumEntityCopier copier;

    @BeforeEach
    void setUp() {
        copier = new CurriculumEntityCopier();
    }

    @Test
    void copyVersionToClassDraft_copiesAllFields() {
        UUID sourceId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UUID createdBy = UUID.randomUUID();

        CurriculumVersion source = createVersion(sourceId, courseId);
        source.setTitle("Original Title");

        CurriculumVersion result = copier.copyVersionToClassDraft(source, classId, createdBy, 5);

        assertThat(result.getCourseId()).isEqualTo(courseId);
        assertThat(result.getClassId()).isEqualTo(classId);
        assertThat(result.getScope()).isEqualTo(CurriculumScope.CLASS);
        assertThat(result.getStatus()).isEqualTo(CurriculumStatus.DRAFT);
        assertThat(result.getVersionNumber()).isEqualTo(5);
        assertThat(result.getTitle()).isEqualTo("Original Title");
        assertThat(result.getSourceVersionId()).isEqualTo(sourceId);
        assertThat(result.getCreatedBy()).isEqualTo(createdBy);
    }

    @Test
    void copyVersionToClassDraft_copiesSections() {
        CurriculumVersion source = createVersion(UUID.randomUUID(), UUID.randomUUID());
        CurriculumSection section = createSection(1);
        source.setSections(new ArrayList<>(List.of(section)));

        CurriculumVersion result = copier.copyVersionToClassDraft(source, UUID.randomUUID(), UUID.randomUUID(), 1);

        assertThat(result.getSections()).hasSize(1);
        assertThat(result.getSections().get(0).getTitle()).isEqualTo("Section 1");
        assertThat(result.getSections().get(0).getSourceCurriculumSectionId()).isEqualTo(section.getId());
    }

    @Test
    void copyVersionToClassDraft_nullSections_handledGracefully() {
        CurriculumVersion source = createVersion(UUID.randomUUID(), UUID.randomUUID());
        source.setSections(null);

        CurriculumVersion result = copier.copyVersionToClassDraft(source, UUID.randomUUID(), UUID.randomUUID(), 1);

        assertThat(result.getSections()).isEmpty();
    }

    @Test
    void copySections_sortsBySortOrder() {
        CurriculumVersion source = createVersion(UUID.randomUUID(), UUID.randomUUID());
        CurriculumSection section1 = createSection(2);
        CurriculumSection section2 = createSection(1);
        CurriculumSection section3 = createSection(3);
        source.setSections(new ArrayList<>(List.of(section1, section2, section3)));

        CurriculumVersion target = new CurriculumVersion();
        copier.copySections(source, target);

        assertThat(target.getSections()).hasSize(3);
        assertThat(target.getSections().get(0).getSortOrder()).isEqualTo(1);
        assertThat(target.getSections().get(1).getSortOrder()).isEqualTo(2);
        assertThat(target.getSections().get(2).getSortOrder()).isEqualTo(3);
    }

    @Test
    void copySection_copiesAllFields() {
        UUID sectionId = UUID.randomUUID();
        CurriculumSection source = createSection(5);
        source.setId(sectionId);
        source.setTitle("Test Section");
        source.setSourceModuleId(UUID.randomUUID());

        CurriculumSection result = copier.copySection(source);

        assertThat(result.getSourceModuleId()).isEqualTo(source.getSourceModuleId());
        assertThat(result.getSourceCurriculumSectionId()).isEqualTo(sectionId);
        assertThat(result.getTitle()).isEqualTo("Test Section");
        assertThat(result.getSortOrder()).isEqualTo(5);
    }

    @Test
    void copySection_copiesLessons() {
        CurriculumSection source = createSection(1);
        CurriculumLesson lesson = createLesson(1);
        source.setLessons(new ArrayList<>(List.of(lesson)));

        CurriculumSection result = copier.copySection(source);

        assertThat(result.getLessons()).hasSize(1);
        assertThat(result.getLessons().get(0).getTitle()).isEqualTo("Lesson 1");
    }

    @Test
    void copyLesson_copiesAllFields() {
        UUID lessonId = UUID.randomUUID();
        UUID lessonIdentityId = UUID.randomUUID();
        UUID testId = UUID.randomUUID();

        CurriculumLesson source = createLesson(3);
        source.setId(lessonId);
        source.setLessonIdentityId(lessonIdentityId);
        source.setTitle("Test Lesson");
        source.setType(LessonType.VIDEO);
        source.setVideoUrl("https://example.com/video");
        source.setContent("Test content");
        source.setDurationSeconds(300);
        source.setTestId(testId);

        CurriculumLesson result = copier.copyLesson(source);

        assertThat(result.getLessonIdentityId()).isEqualTo(lessonIdentityId);
        assertThat(result.getSourceLessonId()).isEqualTo(source.getSourceLessonId());
        assertThat(result.getSourceCurriculumLessonId()).isEqualTo(lessonId);
        assertThat(result.getTitle()).isEqualTo("Test Lesson");
        assertThat(result.getType()).isEqualTo(LessonType.VIDEO);
        assertThat(result.getVideoUrl()).isEqualTo("https://example.com/video");
        assertThat(result.getContent()).isEqualTo("Test content");
        assertThat(result.getDurationSeconds()).isEqualTo(300);
        assertThat(result.getTestId()).isEqualTo(testId);
        assertThat(result.getSortOrder()).isEqualTo(3);
    }

    @Test
    void copyLesson_copiesResources() {
        CurriculumLesson source = createLesson(1);
        CurriculumLessonResource resource = createResource(1);
        source.setResources(new ArrayList<>(List.of(resource)));

        CurriculumLesson result = copier.copyLesson(source);

        assertThat(result.getResources()).hasSize(1);
        assertThat(result.getResources().get(0).getName()).isEqualTo("Resource 1");
    }

    @Test
    void copyResource_copiesAllFields() {
        UUID resourceId = UUID.randomUUID();
        UUID sourceResourceId = UUID.randomUUID();

        CurriculumLessonResource source = createResource(2);
        source.setId(resourceId);
        source.setSourceResourceId(sourceResourceId);
        source.setUrl("https://example.com/file.pdf");
        source.setObjectPath("/files/test.pdf");
        source.setName("Test File");
        source.setFileSize(1024L);
        source.setContentType("application/pdf");

        CurriculumLessonResource result = copier.copyResource(source);

        assertThat(result.getSourceResourceId()).isEqualTo(sourceResourceId);
        assertThat(result.getSourceCurriculumResourceId()).isEqualTo(resourceId);
        assertThat(result.getUrl()).isEqualTo("https://example.com/file.pdf");
        assertThat(result.getObjectPath()).isEqualTo("/files/test.pdf");
        assertThat(result.getName()).isEqualTo("Test File");
        assertThat(result.getFileSize()).isEqualTo(1024L);
        assertThat(result.getContentType()).isEqualTo("application/pdf");
        assertThat(result.getSortOrder()).isEqualTo(2);
    }

    @Test
    void copyLesson_nullResources_handledGracefully() {
        CurriculumLesson source = createLesson(1);
        source.setResources(null);

        CurriculumLesson result = copier.copyLesson(source);

        assertThat(result.getResources()).isEmpty();
    }

    @Test
    void copySection_nullLessons_handledGracefully() {
        CurriculumSection source = createSection(1);
        source.setLessons(null);

        CurriculumSection result = copier.copySection(source);

        assertThat(result.getLessons()).isEmpty();
    }

    // Helper methods to create test entities

    private CurriculumVersion createVersion(UUID id, UUID courseId) {
        CurriculumVersion version = new CurriculumVersion();
        version.setId(id);
        version.setCourseId(courseId);
        version.setTitle("Test Version");
        version.setVersionNumber(1);
        version.setStatus(CurriculumStatus.PUBLISHED);
        version.setScope(CurriculumScope.MASTER);
        version.setCreatedAt(Instant.now());
        return version;
    }

    private CurriculumSection createSection(int sortOrder) {
        CurriculumSection section = new CurriculumSection();
        section.setId(UUID.randomUUID());
        section.setTitle("Section " + sortOrder);
        section.setSortOrder(sortOrder);
        section.setCreatedAt(Instant.now());
        return section;
    }

    private CurriculumLesson createLesson(int sortOrder) {
        CurriculumLesson lesson = new CurriculumLesson();
        lesson.setId(UUID.randomUUID());
        lesson.setTitle("Lesson " + sortOrder);
        lesson.setSortOrder(sortOrder);
        lesson.setType(LessonType.VIDEO);
        lesson.setCreatedAt(Instant.now());
        return lesson;
    }

    private CurriculumLessonResource createResource(int sortOrder) {
        CurriculumLessonResource resource = new CurriculumLessonResource();
        resource.setId(UUID.randomUUID());
        resource.setName("Resource " + sortOrder);
        resource.setSortOrder(sortOrder);
        resource.setCreatedAt(Instant.now());
        return resource;
    }
}
