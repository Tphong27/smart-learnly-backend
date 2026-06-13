package com.smartlearnly.backend.course.repository;

import java.util.UUID;

public interface LearningObjectiveProjection {

	UUID getId();

	String getCode();

	String getDescription();
}
