package com.kanjimastery.backend.service;

import com.kanjimastery.backend.config.JwtProperties;
import com.kanjimastery.backend.dto.AuthResponse;
import com.kanjimastery.backend.dto.LoginRequest;
import com.kanjimastery.backend.dto.RegisterRequest;
import com.kanjimastery.backend.exception.ForbiddenException;
import com.kanjimastery.backend.exception.TooManyRequestsException;
import com.kanjimastery.backend.model.User;
import com.kanjimastery.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UserService userService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private TokenBlacklistService tokenBlacklistService;
    @Mock
    private JwtProperties jwtProperties;
    @Mock
    private LoginAttemptService loginAttemptService;

    @InjectMocks
    private AuthService authService;

    @Test
    void login_shouldNotCheckPassword_whenAccountIsLocked() {
        doThrow(new TooManyRequestsException("Tài khoản tạm khoá")).when(loginAttemptService).ensureNotLocked("taro");

        assertThatThrownBy(() -> authService.login(loginRequest("taro", "sai-mat-khau")))
                .isInstanceOf(TooManyRequestsException.class);
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void login_shouldRecordFailure_whenPasswordIsWrong() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(loginRequest("taro", "sai-mat-khau")))
                .isInstanceOf(BadCredentialsException.class);
        verify(loginAttemptService).recordFailure("taro");
        verify(loginAttemptService, never()).recordSuccess(anyString());
    }

    @Test
    void login_shouldClearFailures_whenPasswordIsCorrect() {
        User user = User.builder().id(7L).username("taro").build();
        when(userService.getByUsername("taro")).thenReturn(user);
        stubTokens(user);

        AuthResponse response = authService.login(loginRequest("taro", "dung-mat-khau"));

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        verify(loginAttemptService).recordSuccess("taro");
        verify(loginAttemptService, never()).recordFailure(anyString());
    }

    @Test
    void register_shouldBeRejected_whenRegistrationIsDisabled() {
        ReflectionTestUtils.setField(authService, "registrationEnabled", false);

        assertThatThrownBy(() -> authService.register(new RegisterRequest()))
                .isInstanceOf(ForbiddenException.class);
        verify(userService, never()).register(any());
    }

    @Test
    void register_shouldCreateAccount_whenRegistrationIsEnabled() {
        ReflectionTestUtils.setField(authService, "registrationEnabled", true);
        RegisterRequest request = new RegisterRequest();
        User user = User.builder().id(8L).username("hanako").build();
        when(userService.register(request)).thenReturn(user);
        stubTokens(user);

        assertThat(authService.register(request).getAccessToken()).isEqualTo("access-token");
    }

    private void stubTokens(User user) {
        when(jwtService.generateAccessToken(user)).thenReturn(new JwtService.GeneratedToken("access-token", "jti-a", new Date()));
        when(jwtService.generateRefreshToken(user)).thenReturn(new JwtService.GeneratedToken("refresh-token", "jti-r", new Date()));
        when(jwtProperties.getAccessTokenExpirationMs()).thenReturn(900_000L);
    }

    private static LoginRequest loginRequest(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }
}
