package com.kanjimastery.backend.dto;

/** Tài khoản vừa cấp kèm mật khẩu tạm - chỉ trả về đúng lần tạo này, không lưu dạng rõ ở đâu cả. */
public record CreatedUserResponse(AdminUserResponse user, String temporaryPassword) {
}
