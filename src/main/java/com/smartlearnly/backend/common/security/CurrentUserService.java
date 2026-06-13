package com.smartlearnly.backend.common.security;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUserService {
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final UserRepository userRepository;

    public UserAccount requireAuthenticatedUser() {
        CurrentUser currentUser = authenticatedUserResolver.resolve()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));

        if (currentUser.id() != null) {
            return userRepository.findByIdAndDeletedAtIsNull(currentUser.id())
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.RESOURCE_NOT_FOUND,
                            "Authenticated user was not found"
                    ));
        }

        if (currentUser.authUserId() != null) {
            Optional<UserAccount> userByAuthUserId =
                    userRepository.findByAuthUserIdAndDeletedAtIsNull(currentUser.authUserId());
            if (userByAuthUserId.isPresent()) {
                return userByAuthUserId.get();
            }
        }

        if (currentUser.email() != null && !currentUser.email().isBlank()) {
            return userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(currentUser.email())
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.RESOURCE_NOT_FOUND,
                            "Authenticated user was not found"
                    ));
        }

        throw new BusinessException(ErrorCode.UNAUTHENTICATED, "Authenticated user identity is missing required claims");
    }

    public UserAccount requireAdmin() {
        UserAccount user = requireAuthenticatedUser();
        if (!"active".equalsIgnoreCase(user.getStatus()) || !"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Admin permission is required");
        }
        return user;
    }
}
