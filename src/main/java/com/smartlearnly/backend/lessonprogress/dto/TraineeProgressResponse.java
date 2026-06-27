package com.smartlearnly.backend.lessonprogress.dto;

import java.util.List;

public record TraineeProgressResponse(
        int totalCourses,
        int completedCourses,
        int inProgressCourses,
        List<CourseProgressItemResponse> courses,
        List<CourseProgressItemResponse> completedCourseItems,
        List<CourseProgressItemResponse> inProgressCourseItems
) {
}
