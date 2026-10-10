package com.kanjimastery.backend.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.exception.ResourceNotFoundException;
import com.kanjimastery.backend.dto.ChangePasswordRequest;
import com.kanjimastery.backend.dto.RegisterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.kanjimastery.backend.model.User;
import com.kanjimastery.backend.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class UserService {

    /** BCrypt only reads the first 72 bytes, so Spring Security refuses to hash a longer password. */
    private static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Transactional
    public User register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Tên đăng nhập đã tồn tại");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email đã được sử dụng");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(hash(request.getPassword()))
                .role("ROLE_USER")
                .createdAt(LocalDateTime.now(clock))
                .build();

        return userRepository.save(user);
    }

    public User getByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + username));
    }

    @Transactional
    public User changePassword(String username, ChangePasswordRequest request) {
        User user = getByUsername(username);
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Mật khẩu hiện tại không đúng");
        }
        user.setPasswordHash(hash(request.getNewPassword()));
        return userRepository.save(user);
    }

    /** Counted in UTF-8 bytes: a Vietnamese letter with diacritics takes 2-3 bytes, so the limit comes sooner. */
    private String hash(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new BadRequestException(
                    "Mật khẩu quá dài: tối đa 72 byte, tức khoảng 72 chữ không dấu, ít hơn nếu có chữ có dấu");
        }
        return passwordEncoder.encode(password);
    }
}
