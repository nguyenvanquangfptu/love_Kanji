package com.kanjimastery.backend.dto;

import com.kanjimastery.backend.model.User;

import java.time.LocalDateTime;

/** Một tài khoản trên trang quản trị - không có mật khẩu. */
public record AdminUserResponse(Long id, String username, String email, String role, boolean mustChangePassword,
                                LocalDateTime createdAt) {

    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole(),
                user.isMustChangePassword(), user.getCreatedAt());
    }
}
