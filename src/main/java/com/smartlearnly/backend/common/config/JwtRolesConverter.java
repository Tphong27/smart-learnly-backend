package com.smartlearnly.backend.common.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtRolesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
    private final SecurityProperties securityProperties;

    @Override
    public Collection<GrantedAuthority> convert(Jwt source) {
        Object rolesClaim = source.getClaim(securityProperties.getJwt().getRolesClaim());
        List<GrantedAuthority> authorities = new ArrayList<>();

        if (rolesClaim instanceof Collection<?> collection) {
            collection.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .map(this::toAuthority)
                    .forEach(authorities::add);
        }
        else if (rolesClaim instanceof String roleValue && !roleValue.isBlank()) {
            for (String role : roleValue.split(",")) {
                if (!role.isBlank()) {
                    authorities.add(toAuthority(role.trim()));
                }
            }
        }

        return authorities;
    }

    private GrantedAuthority toAuthority(String role) {
        String prefix = securityProperties.getJwt().getRolePrefix();
        if (prefix != null && !prefix.isBlank() && !role.startsWith(prefix)) {
            return new SimpleGrantedAuthority(prefix + role);
        }
        return new SimpleGrantedAuthority(role);
    }
}
