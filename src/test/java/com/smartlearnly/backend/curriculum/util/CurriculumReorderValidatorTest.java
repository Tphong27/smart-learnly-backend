package com.smartlearnly.backend.curriculum.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartlearnly.backend.common.exception.BusinessException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CurriculumReorderValidatorTest {

    @Test
    void assertMatchesAllItems_validRequest_doesNotThrow() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        List<UUID> requestedIds = List.of(id1, id2);
        Set<UUID> existingIds = Set.of(id1, id2);

        assertThatCode(() ->
            CurriculumReorderValidator.assertMatchesAllItems(requestedIds, existingIds, "Lesson"))
            .doesNotThrowAnyException();
    }

    @Test
    void assertMatchesAllItems_missingItem_throwsException() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        List<UUID> requestedIds = List.of(id1, id2);
        Set<UUID> existingIds = Set.of(id1, id2, id3);

        assertThatThrownBy(() ->
            CurriculumReorderValidator.assertMatchesAllItems(requestedIds, existingIds, "Section"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Section reorder request must include every item exactly once");
    }

    @Test
    void assertMatchesAllItems_extraItem_throwsException() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        List<UUID> requestedIds = List.of(id1, id2, id3);
        Set<UUID> existingIds = Set.of(id1, id2);

        assertThatThrownBy(() ->
            CurriculumReorderValidator.assertMatchesAllItems(requestedIds, existingIds, "Resource"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Resource reorder request must include every item exactly once");
    }

    @Test
    void assertMatchesAllItems_duplicateIds_throwsException() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        List<UUID> requestedIds = List.of(id1, id1, id2);
        Set<UUID> existingIds = Set.of(id1, id2);

        assertThatThrownBy(() ->
            CurriculumReorderValidator.assertMatchesAllItems(requestedIds, existingIds, "Lesson"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Lesson reorder list contains duplicate ids");
    }

    @Test
    void assertMatchesAllItems_wrongOrder_isValid() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        List<UUID> requestedIds = List.of(id2, id1);
        Set<UUID> existingIds = Set.of(id1, id2);

        assertThatCode(() ->
            CurriculumReorderValidator.assertMatchesAllItems(requestedIds, existingIds, "Section"))
            .doesNotThrowAnyException();
    }

    @Test
    void assertMatchesAllItems_emptyLists_doesNotThrow() {
        List<UUID> requestedIds = List.of();
        Set<UUID> existingIds = Set.of();

        assertThatCode(() ->
            CurriculumReorderValidator.assertMatchesAllItems(requestedIds, existingIds, "Lesson"))
            .doesNotThrowAnyException();
    }
}
