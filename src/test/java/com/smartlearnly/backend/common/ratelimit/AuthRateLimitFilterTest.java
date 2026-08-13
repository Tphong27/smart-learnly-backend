package com.smartlearnly.backend.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthRateLimitFilterTest {
    @Test
    void preflightRequestShouldBypassRateLimit() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(propertiesWithCapacity(0));
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/auth/login");
        request.addHeader(HttpHeaders.ORIGIN, "http://localhost:5173");
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.doFilter(request, response, invokingChain(chainInvoked));

        assertThat(chainInvoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void profileRequestShouldBypassRateLimit() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(propertiesWithCapacity(0));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/profile");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.doFilter(request, response, invokingChain(chainInvoked));

        assertThat(chainInvoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void loginRequestShouldReturnTooManyRequestsAfterCapacityIsExceeded() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(propertiesWithCapacity(1));

        AtomicBoolean firstChainInvoked = new AtomicBoolean(false);
        filter.doFilter(
                new MockHttpServletRequest("POST", "/api/v1/auth/login"),
                new MockHttpServletResponse(),
                invokingChain(firstChainInvoked)
        );

        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        AtomicBoolean secondChainInvoked = new AtomicBoolean(false);
        filter.doFilter(
                new MockHttpServletRequest("POST", "/api/v1/auth/login"),
                secondResponse,
                invokingChain(secondChainInvoked)
        );

        assertThat(firstChainInvoked).isTrue();
        assertThat(secondChainInvoked).isFalse();
        assertThat(secondResponse.getStatus()).isEqualTo(429);
        assertThat(secondResponse.getHeader(HttpHeaders.RETRY_AFTER)).isNotBlank();
        assertThat(secondResponse.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
    }

    @Test
    void disabledRateLimitShouldBypassLoginRequest() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(disabledPropertiesWithCapacity(0));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.doFilter(request, response, invokingChain(chainInvoked));

        assertThat(chainInvoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void nonPostAuthRequestShouldBypassRateLimit() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(propertiesWithCapacity(0));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.doFilter(request, response, invokingChain(chainInvoked));

        assertThat(chainInvoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void postToNonRateLimitedAuthEndpointShouldBypassRateLimit() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(propertiesWithCapacity(0));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/profile");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.doFilter(request, response, invokingChain(chainInvoked));

        assertThat(chainInvoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void loginRequestAtExactCapacityShouldBeAllowed() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(propertiesWithCapacity(2));
        AtomicBoolean firstChainInvoked = new AtomicBoolean(false);
        AtomicBoolean secondChainInvoked = new AtomicBoolean(false);

        filter.doFilter(
                new MockHttpServletRequest("POST", "/api/v1/auth/login"),
                new MockHttpServletResponse(),
                invokingChain(firstChainInvoked)
        );
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(
                new MockHttpServletRequest("POST", "/api/v1/auth/login"),
                secondResponse,
                invokingChain(secondChainInvoked)
        );

        assertThat(firstChainInvoked).isTrue();
        assertThat(secondChainInvoked).isTrue();
        assertThat(secondResponse.getStatus()).isEqualTo(200);
    }

    @Test
    void differentClientIpsShouldHaveIndependentCounters() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(propertiesWithCapacity(1));
        AtomicBoolean firstIpChainInvoked = new AtomicBoolean(false);
        AtomicBoolean secondIpChainInvoked = new AtomicBoolean(false);

        filter.doFilter(
                loginRequestFrom("10.0.0.1"),
                new MockHttpServletResponse(),
                invokingChain(firstIpChainInvoked)
        );
        MockHttpServletResponse secondIpResponse = new MockHttpServletResponse();
        filter.doFilter(
                loginRequestFrom("10.0.0.2"),
                secondIpResponse,
                invokingChain(secondIpChainInvoked)
        );

        assertThat(firstIpChainInvoked).isTrue();
        assertThat(secondIpChainInvoked).isTrue();
        assertThat(secondIpResponse.getStatus()).isEqualTo(200);
    }

    @Test
    void forwardedForShouldUseFirstClientIpForRateLimitCounter() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(propertiesWithCapacity(1));
        AtomicBoolean firstChainInvoked = new AtomicBoolean(false);
        AtomicBoolean secondChainInvoked = new AtomicBoolean(false);

        filter.doFilter(
                loginRequestWithForwardedFor("10.0.0.1, 10.0.0.2"),
                new MockHttpServletResponse(),
                invokingChain(firstChainInvoked)
        );
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(
                loginRequestWithForwardedFor("10.0.0.1, 10.0.0.9"),
                secondResponse,
                invokingChain(secondChainInvoked)
        );

        assertThat(firstChainInvoked).isTrue();
        assertThat(secondChainInvoked).isFalse();
        assertThat(secondResponse.getStatus()).isEqualTo(429);
        assertThat(secondResponse.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
    }

    private AuthRateLimitProperties propertiesWithCapacity(int capacity) {
        AuthRateLimitProperties properties = new AuthRateLimitProperties();
        properties.setEnabled(true);
        properties.setCapacity(capacity);
        return properties;
    }

    private AuthRateLimitProperties disabledPropertiesWithCapacity(int capacity) {
        AuthRateLimitProperties properties = propertiesWithCapacity(capacity);
        properties.setEnabled(false);
        return properties;
    }

    private MockHttpServletRequest loginRequestFrom(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr(remoteAddress);
        return request;
    }

    private MockHttpServletRequest loginRequestWithForwardedFor(String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }

    private FilterChain invokingChain(AtomicBoolean invoked) {
        return (request, response) -> invoked.set(true);
    }
}
