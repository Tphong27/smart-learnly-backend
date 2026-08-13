package com.smartlearnly.backend.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

class NotificationRepositoryQueryTest {
    @Test
    void listQueryShouldUseSimpleDerivedOrderingWithoutArchiveFilter() throws Exception {
        Method method = NotificationRepository.class.getMethod(
                "findByUserIdOrderByCreatedAtDesc",
                UUID.class,
                Pageable.class);

        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNull();
    }

    @Test
    void retentionQueryShouldDeleteOnlyReadNotifications() throws Exception {
        Method method = NotificationRepository.class.getMethod(
                "deleteReadCreatedBefore",
                Instant.class);

        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value())
                .contains("notification.readAt IS NOT NULL")
                .doesNotContain("archivedAt");
    }
}
