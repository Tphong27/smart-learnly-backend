package com.smartlearnly.backend.common.config;

import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
        private final JwtRolesConverter jwtRolesConverter;
        private final SecurityProperties securityProperties;

        @Bean
        @ConditionalOnProperty(prefix = "app.security", name = "authentication-mode", havingValue = "basic", matchIfMissing = true)
        SecurityFilterChain basicSecurityFilterChain(HttpSecurity http) throws Exception {
                http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.POST, "/api/v1/payments/webhooks/sepay")
                .permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/internal/hls/jobs/callback")
                .permitAll()
                .requestMatchers(HttpMethod.GET,
                                "/api/v1/categories",
                                "/api/v1/courses",
                                "/api/v1/courses/search",
                                "/api/v1/courses/category/{categorySlug}",
                                "/api/v1/courses/{slug}",
                                "/api/v1/users/trainers/*/profile",
                                "/api/v1/opening-schedules",
                                "/api/v1/opening-schedules/**")
                .permitAll()
                .requestMatchers(
                                "/api/v1/auth/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**")
                .permitAll()
                .requestMatchers(HttpMethod.GET,
                                "/api/v1/courses/*/preview-lessons",
                                "/api/v1/courses/*/preview-lessons/*")
                .permitAll()
                .requestMatchers(HttpMethod.GET,
                                "/api/v1/courses/*/preview",
                                "/api/v1/courses/{courseId}/preview")
                .permitAll()
                .requestMatchers(HttpMethod.GET,
                                "/api/v1/hls/token/*",
                                "/api/v1/hls/playlist/*",
                                "/api/v1/hls/variant/**",
                                "/api/v1/hls/key/*",
                                "/api/v1/hls/segment/**",
                                "/api/v1/hls/free_video/*")
                .permitAll()
                .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());

                return http.build();
        }

        @Bean
        @ConditionalOnProperty(prefix = "app.security", name = "authentication-mode", havingValue = "jwt")
        SecurityFilterChain jwtSecurityFilterChain(HttpSecurity http) throws Exception {
                http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/webhooks/sepay")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.POST, "/api/v1/internal/hls/jobs/callback")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET,
                                                        "/api/v1/categories",
                                                        "/api/v1/courses",
                                                        "/api/v1/courses/search",
                                                        "/api/v1/courses/category/{categorySlug}",
                                                        "/api/v1/courses/{slug}",
                                                        "/api/v1/users/trainers/*/profile",
                                                        "/api/v1/opening-schedules",
                                                        "/api/v1/opening-schedules/**")
                                        .permitAll()
                                        .requestMatchers(
                                                        "/api/v1/auth/register",
                                                        "/api/v1/auth/login",
                                                        "/api/v1/auth/google",
                                                        "/api/v1/auth/google/config",
                                                        "/api/v1/auth/refresh",
                                                        "/api/v1/auth/logout",
                                                        "/api/v1/auth/forgot-password",
                                                        "/api/v1/auth/reset-password",
                                                        "/api/v1/auth/verify-email",
                                                        "/api/v1/auth/resend-verification",
                                                        "/v3/api-docs/**",
                                                        "/swagger-ui.html",
                                                        "/swagger-ui/**")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET,
                                                        "/api/v1/courses/*/preview-lessons/*")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET,
                                                        "/api/v1/courses/*/preview",
                                                        "/api/v1/courses/{courseId}/preview")
                                        .permitAll()
                                        // Playback endpoints authenticate with short-lived HLS
                                        // query tokens. Upload/status remain JWT + role protected.
                                        .requestMatchers(HttpMethod.GET,
                                                        "/api/v1/hls/token/*",
                                                        "/api/v1/hls/playlist/*",
                                                        "/api/v1/hls/variant/**",
                                                        "/api/v1/hls/key/*",
                                                        "/api/v1/hls/segment/**",
                                                        "/api/v1/hls/free_video/*")
                                        .permitAll()
                                        // Admin endpoints: allow ADMIN/TMO/SME/TRAINER to access course
                                        // management APIs
                                        .requestMatchers("/api/v1/admin/question-banks/**",
                                                        "/api/v1/admin/questions/**",
                                                        "/api/v1/admin/test-questions/**")
                                        .hasAnyRole("ADMIN", "SME", "TMO", "TRAINER")
                                        .requestMatchers("/api/v1/admin/question-imports/**",
                                                        "/api/v1/admin/question-answers/**")
                                        .hasAnyRole("ADMIN", "SME")
                                        // Admin course content management: allow ADMIN/TMO/SME/TRAINER to
                                        // access course content authoring APIs
                                        // Course list/detail
                                        .requestMatchers(HttpMethod.GET,
                                                        "/api/v1/admin/courses",
                                                        "/api/v1/admin/courses/**")
                                        .hasAnyRole("ADMIN", "TMO", "SME", "TRAINER")
                                         // Only Admin/TMO can create courses
                                        .requestMatchers(HttpMethod.POST,
                                                        "/api/v1/admin/courses")
                                        .hasAnyRole("ADMIN", "TMO")
                                        // Admin/TMO can update every course.
                                        // SME/Trainer still require assignment checks in CourseAccessService.
                                        .requestMatchers(HttpMethod.PATCH,
                                                        "/api/v1/admin/courses/*")
                                        .hasAnyRole("ADMIN", "TMO", "SME", "TRAINER")
                                        // Only Admin/TMO can delete courses
                                        .requestMatchers(HttpMethod.DELETE,
                                                        "/api/v1/admin/courses/*")
                                        .hasAnyRole("ADMIN", "TMO")
                                        //
                                        .requestMatchers("/api/v1/admin/sections/**")
                                        .hasAnyRole("ADMIN", "TMO", "SME", "TRAINER")
                                        .requestMatchers("/api/v1/admin/lessons/**")
                                        .hasAnyRole("ADMIN", "TMO", "SME", "TRAINER")
                                        .requestMatchers(
                                                        "/api/v1/admin/flashcard-sets/**",
                                                        "/api/v1/admin/flashcard-cards/**",
                                                        "/api/v1/admin/flashcard-staging-cards/**")
                                        .hasAnyRole("ADMIN", "TMO", "SME", "TRAINER")
                                        .requestMatchers(HttpMethod.GET,
                                                        "/api/v1/admin/categories",
                                                        "/api/v1/admin/categories/**")
                                        .hasAnyRole("ADMIN", "TMO", "SME", "TRAINER")
                                        // GET class list/detail: mở cho ADMIN/TMO/SME/TRAINER để staff các role
                                        // có thể duyệt lớp.
                                        .requestMatchers(HttpMethod.GET,
                                                        "/api/v1/admin/classes",
                                                        "/api/v1/admin/classes/**")
                                        .hasAnyRole("ADMIN", "TMO", "SME", "TRAINER")
                                        // Các thao tác write vẫn giữ ADMIN/TMO
                                        .requestMatchers("/api/v1/admin/classes/**", "/api/v1/admin/classes")
                                        .hasAnyRole("ADMIN", "TMO")
                                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/users",
                                                        "/api/v1/admin/users/**")
                                        .hasAnyRole("ADMIN", "TMO")
                                        // Uploads: trainer cần upload material/resource/media khi tuỳ biến
                                        // class curriculum.
                                        .requestMatchers("/api/v1/admin/uploads/**")
                                        .hasAnyRole("ADMIN", "TMO", "SME", "TRAINER")
                                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                                        .anyRequest().authenticated())
                                .oauth2ResourceServer(oauth2 -> oauth2.jwt(
                                                jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

                return http.build();
        }

        @Bean
        @ConditionalOnProperty(prefix = "app.security", name = "authentication-mode", havingValue = "jwt")
        @ConditionalOnExpression("'${app.security.jwt-secret:${JWT_SECRET:}}' != ''")
        JwtDecoder jwtDecoder() {
                String jwtSecret = resolveJwtSecret();
                if (jwtSecret == null || jwtSecret.isBlank()) {
                        throw new IllegalStateException(
                                        "JWT_SECRET must be configured when app.security.jwt-secret is used for JWT mode.");
                }
                return NimbusJwtDecoder.withSecretKey(
                                new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")).build();
        }

        @Bean
        JwtEncoder jwtEncoder() {
                String jwtSecret = resolveJwtSecret();
                if (jwtSecret == null || jwtSecret.length() < 32) {
                        throw new IllegalStateException("JWT_SECRET must contain at least 32 characters.");
                }
                return NimbusJwtEncoder.withSecretKey(
                                new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")).build();
        }

        @Bean
        PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        private org.springframework.core.convert.converter.Converter<org.springframework.security.oauth2.jwt.Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
                JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
                converter.setJwtGrantedAuthoritiesConverter(jwtRolesConverter);
                return converter;
        }

        private String resolveJwtSecret() {
                if (securityProperties.getJwtSecret() != null && !securityProperties.getJwtSecret().isBlank()) {
                        return securityProperties.getJwtSecret();
                }
                return System.getenv("JWT_SECRET");
        }
}