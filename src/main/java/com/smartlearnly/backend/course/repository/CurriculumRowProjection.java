package com.smartlearnly.backend.course.repository;

import java.util.UUID;

public interface CurriculumRowProjection {

	UUID getModuleId();

	String getModuleTitle();

	int getModuleOrderIndex();

	UUID getLessonId();

	String getLessonTitle();

	String getLessonType();

	Integer getLessonOrderIndex();

	Boolean getLessonPreview();
}
