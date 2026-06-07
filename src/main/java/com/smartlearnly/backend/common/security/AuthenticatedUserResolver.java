package com.smartlearnly.backend.common.security;

import java.util.Optional;

public interface AuthenticatedUserResolver {
    Optional<CurrentUser> resolve();
}
