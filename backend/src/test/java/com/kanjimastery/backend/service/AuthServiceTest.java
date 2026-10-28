package com.kanjimastery.backend.service;

import com.kanjimastery.backend.config.JwtProperties;
import com.kanjimastery.backend.dto.AuthResponse;
import com.kanjimastery.backend.dto.ChangePasswordRequest;
import com.kanjimastery.backend.dto.LoginRequest;
import com.kanjimastery.backend.dto.RefreshTokenRequest;
import com.kanjimastery.backend.dto.RegisterRequest;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.exception.ForbiddenException;
import com.kanjimastery.backend.exception.TooManyRequestsException;
import com.kanjimastery.backend.exception.UnauthorizedException;
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

import java.time.Duration;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Test
    void refresh_shouldRefuseAnAccessToken_withoutEndingTheLearnersSessions() {
        when(jwtService.isTokenValid("access-token", JwtService.TokenType.REFRESH)).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh(refreshRequest("access-token")))
                .isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void logout_shouldOnlyBlacklistAnAccessToken_andOnlyRevokeARefreshToken() {
        // Swapped tokens: neither is of the type expected in its place, so nothing is blacklisted or revoked.
        when(jwtService.isTokenValid("refresh-token", JwtService.TokenType.ACCESS)).thenReturn(false);
        when(jwtService.isTokenValid("access-token", JwtService.TokenType.REFRESH)).thenReturn(false);

        authService.logout("refresh-token", "access-token");

        verifyNoInteractions(tokenBlacklistService, refreshTokenService);
    }

    @Test
    void login_shouldTellTheClient_whenTheAccountStillHasATemporaryPassword() {
        User user = User.builder().id(9L).username("hanako").mustChangePassword(true).build();
        when(userService.getByUsername("hanako")).thenReturn(user);
        stubTokens(user);

        assertThat(authService.login(loginRequest("hanako", "mat-khau-tam")).isMustChangePassword()).isTrue();
    }

    @Test
    void changePassword_shouldEndEverySession_andBlacklistThisAccessToken() {
        ChangePasswordRequest request = changeRequest("old-password", "new-password123");
        User user = User.builder().id(7L).username("taro").build();
        when(userService.getByUsername("taro")).thenReturn(user);
        when(userService.passwordMatches(user, "old-password")).thenReturn(true);
        when(jwtService.isTokenValid("access-token", JwtService.TokenType.ACCESS)).thenReturn(true);
        when(jwtService.extractJti("access-token")).thenReturn("jti-a");
        when(jwtService.extractExpiration("access-token")).thenReturn(new Date(System.currentTimeMillis() + 60_000));

        authService.changePassword("taro", "access-token", request);

        verify(userService).setPassword(user, "new-password123");
        verify(loginAttemptService).recordSuccess("taro");
        verify(refreshTokenService).revokeAll(7L);
        verify(tokenBlacklistService).blacklist(eq("jti-a"), any(Duration.class));
    }

    @Test
    void changePassword_shouldCountAWrongOldPasswordAsAFailedLogin_andKeepTheSessions() {
        ChangePasswordRequest request = changeRequest("wrong-password", "new-password123");
        User user = User.builder().id(7L).username("taro").build();
        when(userService.getByUsername("taro")).thenReturn(user);
        when(userService.passwordMatches(user, "wrong-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword("taro", "access-token", request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Mật khẩu hiện tại không đúng");
        verify(loginAttemptService).recordFailure("taro");
        verify(userService, never()).setPassword(any(), anyString());
        verifyNoInteractions(refreshTokenService, tokenBlacklistService);
    }

    @Test
    void changePassword_shouldLockTheAccount_whenTheOldPasswordIsGuessedTooOften() {
        ChangePasswordRequest request = changeRequest("wrong-password", "new-password123");
        User user = User.builder().id(7L).username("taro").build();
        when(userService.getByUsername("taro")).thenReturn(user);
        when(userService.passwordMatches(user, "wrong-password")).thenReturn(false);
        doThrow(new TooManyRequestsException("Tài khoản tạm khoá")).when(loginAttemptService).recordFailure("taro");

        assertThatThrownBy(() -> authService.changePassword("taro", "access-token", request))
                .isInstanceOf(TooManyRequestsException.class);
        verify(userService, never()).setPassword(any(), anyString());
    }

    @Test
    void changePassword_shouldNotCheckThePassword_whenTheAccountIsLocked() {
        doThrow(new TooManyRequestsException("Tài khoản tạm khoá")).when(loginAttemptService).ensureNotLocked("taro");

        assertThatThrownBy(() -> authService.changePassword("taro", "access-token",
                changeRequest("old-password", "new-password123")))
                .isInstanceOf(TooManyRequestsException.class);
        verify(userService, never()).passwordMatches(any(), anyString());
        verify(userService, never()).setPassword(any(), anyString());
    }

    private static ChangePasswordRequest changeRequest(String oldPassword, String newPassword) {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword(oldPassword);
        request.setNewPassword(newPassword);
        return request;
    }

    private static RefreshTokenRequest refreshRequest(String token) {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(token);
        return request;
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
