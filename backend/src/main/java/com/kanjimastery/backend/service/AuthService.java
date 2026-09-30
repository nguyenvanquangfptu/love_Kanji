package com.kanjimastery.backend.service;

import com.kanjimastery.backend.exception.ForbiddenException;
import com.kanjimastery.backend.exception.UnauthorizedException;
import com.kanjimastery.backend.model.User;
import com.kanjimastery.backend.repository.UserRepository;
import com.kanjimastery.backend.service.UserService;
import com.kanjimastery.backend.dto.AuthResponse;
import com.kanjimastery.backend.dto.LoginRequest;
import com.kanjimastery.backend.dto.RefreshTokenRequest;
import com.kanjimastery.backend.dto.RegisterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Date;
import com.kanjimastery.backend.config.JwtProperties;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final JwtProperties jwtProperties;
    private final LoginAttemptService loginAttemptService;

    /** Tắt khi đưa app lên mạng để người lạ không tự tạo tài khoản (biến môi trường REGISTRATION_ENABLED). */
    @Value("${app.auth.registration-enabled:true}")
    private boolean registrationEnabled;

    public AuthResponse register(RegisterRequest request) {
        if (!registrationEnabled) {
            throw new ForbiddenException("Đăng ký tài khoản mới đang tắt. Hãy liên hệ quản trị viên để được cấp tài khoản.");
        }
        User user = userService.register(request);
        return issueTokens(user);
    }

    public AuthResponse login(LoginRequest request) {
        String username = request.getUsername();
        loginAttemptService.ensureNotLocked(username);
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, request.getPassword()));
        } catch (BadCredentialsException ex) {
            loginAttemptService.recordFailure(username);
            throw ex;
        }
        loginAttemptService.recordSuccess(username);
        User user = userService.getByUsername(username);
        return issueTokens(user);
    }

    /**
     * Refresh Token Rotation: mỗi refresh phát hành token mới và revoke ngay token cũ.
     * Nếu token đưa lên không còn hợp lệ trong Redis (đã bị rotate/dùng trước đó),
     * coi đây là dấu hiệu bị đánh cắp và thu hồi toàn bộ session của user.
     */
    public AuthResponse refresh(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();
        if (!jwtService.isTokenValid(refreshToken)) {
            throw new UnauthorizedException("Refresh token không hợp lệ hoặc đã hết hạn");
        }

        Long userId = jwtService.extractUserId(refreshToken);
        String jti = jwtService.extractJti(refreshToken);

        if (!refreshTokenService.isValid(userId, jti)) {
            refreshTokenService.revokeAll(userId);
            throw new UnauthorizedException("Refresh token đã được sử dụng hoặc không còn hiệu lực. Vui lòng đăng nhập lại.");
        }

        refreshTokenService.revoke(userId, jti);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Người dùng không tồn tại"));
        return issueTokens(user);
    }

    public void logout(String accessToken, String refreshToken) {
        if (accessToken != null && jwtService.isTokenValid(accessToken)) {
            String jti = jwtService.extractJti(accessToken);
            Date expiration = jwtService.extractExpiration(accessToken);
            long ttlMs = expiration.getTime() - System.currentTimeMillis();
            tokenBlacklistService.blacklist(jti, Duration.ofMillis(Math.max(ttlMs, 0)));
        }

        if (refreshToken != null && jwtService.isTokenValid(refreshToken)) {
            Long userId = jwtService.extractUserId(refreshToken);
            String jti = jwtService.extractJti(refreshToken);
            refreshTokenService.revoke(userId, jti);
        }
    }

    private AuthResponse issueTokens(User user) {
        JwtService.GeneratedToken access = jwtService.generateAccessToken(user);
        JwtService.GeneratedToken refresh = jwtService.generateRefreshToken(user);
        refreshTokenService.store(user.getId(), refresh.jti());

        return AuthResponse.builder()
                .accessToken(access.token())
                .refreshToken(refresh.token())
                .tokenType("Bearer")
                .expiresInSeconds(jwtProperties.getAccessTokenExpirationMs() / 1000)
                .build();
    }
}
