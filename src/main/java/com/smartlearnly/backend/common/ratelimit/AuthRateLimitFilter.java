package com.smartlearnly.backend.common.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private final AuthRateLimitProperties properties;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.isEnabled()
                || !request.getRequestURI().startsWith("/api/v1/auth/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String key = clientIp(request);
        Instant now = Instant.now();

        WindowCounter counter = counters.compute(key, (ignored, current) -> {
            if (current == null || !current.windowStartedAt().plus(properties.getWindow()).isAfter(now)) {
                return new WindowCounter(now, 1);
            }
            return new WindowCounter(current.windowStartedAt(), current.count() + 1);
        });

        if (counter.count() > properties.getCapacity()) {
            long retryAfterSeconds = Math.max(
                    1,
                    counter.windowStartedAt().plus(properties.getWindow()).getEpochSecond() - now.getEpochSecond()
            );

            response.setStatus(429);
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                    {"success":false,"status":429,"code":"RATE_LIMIT_EXCEEDED","message":"Too many authentication requests. Please try again later.","path":"%s","errors":[]}
                    """.formatted(request.getRequestURI()));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private record WindowCounter(Instant windowStartedAt, int count) {
    }
}