package com.smartlearnly.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class NotificationPayloadsTest {

    @Test
    void ofShouldReturnEmptyMapWhenArgumentsArrayIsNull() {
        Object[] keyValues = null;

        Map<String, Object> payload = NotificationPayloads.of(keyValues);

        assertThat(payload).isEmpty();
    }

    @Test
    void ofShouldReturnEmptyMapWhenNoArgumentsProvided() {
        Map<String, Object> payload = NotificationPayloads.of();

        assertThat(payload).isEmpty();
    }

    @Test
    void ofShouldReturnAllValidPairsAndPreserveInsertionOrder() {
        Map<String, Object> payload = NotificationPayloads.of(
                "courseId", "course-1",
                "lessonId", "lesson-1",
                "score", 95);

        assertThat(payload)
                .containsEntry("courseId", "course-1")
                .containsEntry("lessonId", "lesson-1")
                .containsEntry("score", 95);
        assertThat(payload.keySet()).containsExactly("courseId", "lessonId", "score");
    }

    @Test
    void ofShouldRejectOddNumberOfArguments() {
        assertThatThrownBy(() -> NotificationPayloads.of("courseId", "course-1", "lessonId"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Notification payload requires key/value pairs");
    }

    @Test
    void ofShouldIgnoreNullKey() {
        Map<String, Object> payload = NotificationPayloads.of(null, "course-1");

        assertThat(payload).isEmpty();
    }

    @Test
    void ofShouldIgnoreNullValue() {
        Map<String, Object> payload = NotificationPayloads.of("courseId", null);

        assertThat(payload).isEmpty();
    }

    @Test
    void ofShouldKeepOnlyValidPairsWhenNullPairsAreMixedIn() {
        Map<String, Object> payload = NotificationPayloads.of(
                "courseId", "course-1",
                null, "ignored-key",
                "lessonId", null,
                "status", "ready");

        assertThat(payload).containsExactly(
                Map.entry("courseId", "course-1"),
                Map.entry("status", "ready"));
    }

    @Test
    void ofShouldConvertNonStringKeyUsingStringValueOf() {
        Map<String, Object> payload = NotificationPayloads.of(123, "numeric-key");

        assertThat(payload).containsExactly(Map.entry("123", "numeric-key"));
    }

    @Test
    void ofShouldReplaceEarlierValueForDuplicateKey() {
        Map<String, Object> payload = NotificationPayloads.of(
                "courseId", "course-1",
                "courseId", "course-2");

        assertThat(payload).containsExactly(Map.entry("courseId", "course-2"));
    }
}
