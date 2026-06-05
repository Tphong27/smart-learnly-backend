package com.smartlearnly.backend.common.security;

import java.util.Set;
import java.util.UUID;

public record CurrentUser(
        UUID id,
        String email,
        Set<String> roles
) {
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
