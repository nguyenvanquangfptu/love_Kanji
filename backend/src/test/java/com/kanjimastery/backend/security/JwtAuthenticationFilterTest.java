package com.kanjimastery.backend.security;

import com.kanjimastery.backend.config.JwtProperties;
import com.kanjimastery.backend.service.CustomUserDetailsService;
import com.kanjimastery.backend.service.JwtService;
import com.kanjimastery.backend.service.TokenBlacklistService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class JwtAuthenticationFilterTest {

    private static final String TOKEN = "signed.jwt.token";

    private final JwtService jwtService = mock(JwtService.class);
    private final TokenBlacklistService tokenBlacklistService = mock(TokenBlacklistService.class);
    private final CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(jwtService, tokenBlacklistService, userDetailsService);

    @BeforeEach
    void setUp() {
        when(jwtService.isTokenValid(TOKEN, JwtService.TokenType.ACCESS)).thenReturn(true);
        when(jwtService.extractJti(TOKEN)).thenReturn("jti-1");
        when(jwtService.extractUsername(TOKEN)).thenReturn("alice");
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validToken_shouldAuthenticateTheRequest() throws Exception {
        when(userDetailsService.loadUserByUsername("alice"))
                .thenReturn(User.withUsername("alice").password("x").authorities("ROLE_USER").build());

        MockFilterChain chain = send();

        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("alice");
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void deletedAccount_shouldStayUnauthenticated_withoutWarning(CapturedOutput output) throws Exception {
        when(userDetailsService.loadUserByUsername("alice")).thenThrow(new UsernameNotFoundException("alice"));

        MockFilterChain chain = send();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).as("request vẫn đi tiếp để Spring Security trả 401").isNotNull();
        assertThat(output).doesNotContain("Không xác thực được request");
    }

    @Test
    void redisDown_shouldStayUnauthenticated_andLogTheCause(CapturedOutput output) throws Exception {
        when(tokenBlacklistService.isBlacklisted("jti-1"))
                .thenThrow(new RedisConnectionFailureException("Redis is down"));

        MockFilterChain chain = send();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
        assertThat(output).contains("Không xác thực được request GET /api/v1/srs/daily")
                .contains("RedisConnectionFailureException");
    }

    @Test
    void refreshToken_shouldNotAuthenticateTheRequest() throws Exception {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-only-secret-key-must-be-at-least-32-bytes-long!!");
        properties.setAccessTokenExpirationMs(900_000L);
        properties.setRefreshTokenExpirationMs(604_800_000L);
        JwtService realJwtService = new JwtService(properties);
        JwtAuthenticationFilter realFilter =
                new JwtAuthenticationFilter(realJwtService, tokenBlacklistService, userDetailsService);
        String refreshToken = realJwtService.generateRefreshToken(
                com.kanjimastery.backend.model.User.builder().id(42L).username("alice").role("ROLE_USER").build()).token();

        MockFilterChain chain = send(realFilter, refreshToken);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(userDetailsService);
    }

    private MockFilterChain send() throws Exception {
        return send(filter, TOKEN);
    }

    private static MockFilterChain send(JwtAuthenticationFilter target, String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/srs/daily");
        request.addHeader("Authorization", "Bearer " + token);
        MockFilterChain chain = new MockFilterChain();
        target.doFilter(request, new MockHttpServletResponse(), chain);
        return chain;
    }
}
