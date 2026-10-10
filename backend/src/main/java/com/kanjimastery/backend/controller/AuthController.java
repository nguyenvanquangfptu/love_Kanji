package com.kanjimastery.backend.controller;

import com.kanjimastery.backend.service.AuthService;
import com.kanjimastery.backend.dto.AuthResponse;
import com.kanjimastery.backend.dto.ChangePasswordRequest;
import com.kanjimastery.backend.dto.LoginRequest;
import com.kanjimastery.backend.dto.RefreshTokenRequest;
import com.kanjimastery.backend.dto.RegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Đăng ký, đăng nhập, refresh token (rotation), logout")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(summary = "Refresh Token Rotation",
            description = "Phát hành token mới và revoke ngay token cũ. Dùng lại token đã revoke sẽ bị coi là dấu hiệu bị đánh cắp và thu hồi toàn bộ session.")
    @PostMapping("/refresh-token")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody(required = false) RefreshTokenRequest request) {
        String refreshToken = request != null ? request.getRefreshToken() : null;
        authService.logout(bearerToken(authorizationHeader), refreshToken);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Đổi mật khẩu",
            description = "Đăng xuất mọi phiên, kể cả phiên đang gọi: client phải đăng nhập lại bằng mật khẩu mới.")
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(Authentication authentication,
                                                @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
                                                @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(authentication.getName(), bearerToken(authorizationHeader), request);
        return ResponseEntity.noContent().build();
    }

    private static String bearerToken(String authorizationHeader) {
        return authorizationHeader != null && authorizationHeader.startsWith("Bearer ")
                ? authorizationHeader.substring(7)
                : null;
    }
}
