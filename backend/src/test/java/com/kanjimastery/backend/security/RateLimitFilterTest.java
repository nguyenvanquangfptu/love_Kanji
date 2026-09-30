package com.kanjimastery.backend.security;

import com.kanjimastery.backend.config.RateLimitProperties;
import com.kanjimastery.backend.service.RateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    private final Map<String, Integer> counters = new HashMap<>();
    private final RateLimiterService rateLimiter = mock(RateLimiterService.class);
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        // Bộ đếm trong bộ nhớ thay cho Redis: cho qua khi số lần gọi theo khoá chưa vượt giới hạn.
        when(rateLimiter.tryAcquire(anyString(), anyInt(), any())).thenAnswer(invocation ->
                counters.merge(invocation.getArgument(0), 1, Integer::sum) <= invocation.<Integer>getArgument(1));

        RateLimitProperties properties = new RateLimitProperties();
        properties.getLogin().setLimit(5);
        properties.getLogin().setWindowSeconds(60);
        properties.getRegister().setLimit(3);
        properties.getRegister().setWindowSeconds(60);
        filter = new RateLimitFilter(rateLimiter, properties);
    }

    @Test
    void login_shouldBlockSixthAttempt_evenWhenEachRequestSpoofsADifferentForwardedFor() throws Exception {
        for (int attempt = 1; attempt <= 6; attempt++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            request.setRemoteAddr("203.0.113.7");
            request.addHeader("X-Forwarded-For", "198.51.100." + attempt);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new MockFilterChain());

            assertThat(response.getStatus()).as("lần thử %d", attempt).isEqualTo(attempt <= 5 ? 200 : 429);
        }
    }

    @Test
    void login_shouldCountEachClientAddressSeparately() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            send("/api/v1/auth/login", "203.0.113.7");
        }

        assertThat(send("/api/v1/auth/login", "203.0.113.7").getStatus()).isEqualTo(429);
        assertThat(send("/api/v1/auth/login", "203.0.113.8").getStatus()).isEqualTo(200);
    }

    @Test
    void otherEndpoints_shouldNotBeRateLimited() throws Exception {
        assertThat(send("/api/v1/kanji", "203.0.113.7").getStatus()).isEqualTo(200);
        verify(rateLimiter, never()).tryAcquire(anyString(), anyInt(), any());
    }

    private MockHttpServletResponse send(String path, String remoteAddr) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(remoteAddr);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
