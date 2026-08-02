package com.smartlearnly.backend.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

class NotificationRepositoryQueryTest {
    @Test
    void findForUserShouldCastTypeParameterInDataAndCountQueries() throws Exception {
        Method method = NotificationRepository.class.getMethod(
                "findForUser",
                UUID.class,
                boolean.class,
                boolean.class,
                String.class,
                Pageable.class);

        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.nativeQuery()).isTrue();
        assertThat(query.value())
                .contains("notification.type = CAST(:type AS public.notification_type)")
                .contains("ORDER BY notification.created_at DESC")
                .doesNotContain("notification.type::text")
                .doesNotContain("CAST(notification.type");
        assertThat(query.countQuery())
                .contains("SELECT COUNT(*)")
                .contains("notification.type = CAST(:type AS public.notification_type)")
                .doesNotContain("ORDER BY")
                .doesNotContain("notification.type::text")
                .doesNotContain("CAST(notification.type");
    }
}
