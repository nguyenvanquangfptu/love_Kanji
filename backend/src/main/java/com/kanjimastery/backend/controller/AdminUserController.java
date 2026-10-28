package com.kanjimastery.backend.controller;

import com.kanjimastery.backend.dto.AdminUserResponse;
import com.kanjimastery.backend.dto.CreateUserRequest;
import com.kanjimastery.backend.dto.CreatedUserResponse;
import com.kanjimastery.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Users", description = "Cấp tài khoản người học khi tắt tự đăng ký (chỉ ADMIN)")
public class AdminUserController {

    private final UserService userService;

    @Operation(summary = "Danh sách tài khoản", description = "Mới tạo trước.")
    @GetMapping
    public ResponseEntity<Page<AdminUserResponse>> list(@RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(userService.list(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    }

    @Operation(summary = "Cấp tài khoản người học",
            description = "Luôn là ROLE_USER. Mật khẩu tạm do máy sinh, chỉ trả về trong response này; người học phải "
                    + "đổi mật khẩu ở lần đăng nhập đầu.")
    @PostMapping
    public ResponseEntity<CreatedUserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createAccount(request));
    }
}
