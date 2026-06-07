package com.smartlearnly.backend.common.security;

import com.smartlearnly.backend.common.config.SecurityProperties;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityContextAuthenticatedUserResolver implements AuthenticatedUserResolver {
    private final SecurityProperties securityProperties;

    @Override
    public Optional<CurrentUser> resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }

        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return Optional.of(fromJwt(jwtAuthenticationToken));
        }

        return Optional.of(fromAuthentication(authentication));
    }

    private CurrentUser fromJwt(JwtAuthenticationToken authentication) {
        Jwt jwt = authentication.getToken();
        SecurityProperties.Jwt jwtProperties = securityProperties.getJwt();

        return new CurrentUser(
                parseUuidClaim(jwt.getClaimAsString(jwtProperties.getUserIdClaim())),
                parseUuidClaim(jwt.getClaimAsString(jwtProperties.getAuthUserIdClaim())),
                jwt.getClaimAsString(jwtProperties.getEmailClaim()),
                extractRoles(authentication.getAuthorities(), jwtProperties.getRolePrefix())
        );
    }

    private CurrentUser fromAuthentication(Authentication authentication) {
        return new CurrentUser(
                null,
                null,
                authentication.getName(),
                extractRoles(authentication.getAuthorities(), "ROLE_")
        );
    }

    private Set<String> extractRoles(Collection<? extends GrantedAuthority> authorities, String rolePrefix) {
        if (authorities == null || authorities.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> roles = new LinkedHashSet<>();
        for (GrantedAuthority authority : authorities) {
            if (authority == null || authority.getAuthority() == null) {
                continue;
            }
            String value = authority.getAuthority();
            if (rolePrefix != null && !rolePrefix.isBlank() && value.startsWith(rolePrefix)) {
                roles.add(value.substring(rolePrefix.length()));
            }
            else {
                roles.add(value);
            }
        }
        return Collections.unmodifiableSet(roles);
    }

    private UUID parseUuidClaim(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        }
        catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
