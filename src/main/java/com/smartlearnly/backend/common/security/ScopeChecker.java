package com.smartlearnly.backend.common.security;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ScopeChecker {
    public void requireOwner(UUID ownerId, CurrentUser currentUser) {
        if (currentUser == null || ownerId == null || !ownerId.equals(currentUser.id())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    public void requireRole(CurrentUser currentUser, String role) {
        if (currentUser == null || !currentUser.hasRole(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
