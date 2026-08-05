package com.smartlearnly.backend.curriculum.util;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Validates reorder requests to ensure they contain all items exactly once.
 */
public final class CurriculumReorderValidator {

    private CurriculumReorderValidator() {}

    /**
     * Asserts that reorder request matches all existing items.
     *
     * @param requestedIds the IDs in the reorder request
     * @param existingIds the existing item IDs
     * @param itemName the name of the item type for error messages
     * @throws BusinessException if validation fails
     */
    public static void assertMatchesAllItems(List<UUID> requestedIds, Set<UUID> existingIds, String itemName) {
        Set<UUID> uniqueRequestedIds = new HashSet<>(requestedIds);

        if (uniqueRequestedIds.size() != requestedIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    itemName + " reorder list contains duplicate ids");
        }

        if (!uniqueRequestedIds.equals(existingIds)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    itemName + " reorder request must include every item exactly once"
            );
        }
    }
}
