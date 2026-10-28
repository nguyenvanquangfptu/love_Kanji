package com.kanjimastery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    @Builder.Default
    private String tokenType = "Bearer";
    private long expiresInSeconds;
    /** Còn mật khẩu tạm do quản trị viên cấp: client đưa người học tới trang đổi mật khẩu trước. */
    private boolean mustChangePassword;
}
