package com.kanjimastery.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.exception.ResourceNotFoundException;
import com.kanjimastery.backend.dto.AdminUserResponse;
import com.kanjimastery.backend.dto.CreateUserRequest;
import com.kanjimastery.backend.dto.CreatedUserResponse;
import com.kanjimastery.backend.dto.RegisterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    /** Mật khẩu tạm: bỏ các ký tự dễ đọc nhầm (0/O, 1/l/I) vì người học sẽ gõ lại từ tin nhắn. */
    static final String TEMPORARY_PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    static final int TEMPORARY_PASSWORD_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Transactional
    public User register(RegisterRequest request) {
        return userRepository.save(newLearner(request.getUsername(), request.getEmail(), request.getPassword()));
    }

    /**
     * Tài khoản do quản trị viên cấp (khi tắt tự đăng ký): luôn là người học, mật khẩu tạm do máy sinh và chỉ trả về
     * lần này - người học phải đổi ở lần đăng nhập đầu.
     */
    @Transactional
    public CreatedUserResponse createAccount(CreateUserRequest request) {
        String temporaryPassword = temporaryPassword();
        User user = newLearner(request.getUsername(), request.getEmail(), temporaryPassword);
        user.setMustChangePassword(true);
        User saved = userRepository.save(user);
        return new CreatedUserResponse(AdminUserResponse.from(saved), temporaryPassword);
    }

    /** Các tài khoản, mới tạo trước. */
    @Transactional(readOnly = true)
    public Page<AdminUserResponse> list(int page, int size) {
        Sort newestFirst = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
        return userRepository.findAll(PageRequest.of(page, size, newestFirst)).map(AdminUserResponse::from);
    }

    public User getByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + username));
    }

    public boolean passwordMatches(User user, String rawPassword) {
        return passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }

    /** Đặt mật khẩu mới (đã kiểm tra mật khẩu cũ ở {@link AuthService}); mật khẩu tạm cũng hết bắt đổi. */
    @Transactional
    public User setPassword(User user, String newPassword) {
        user.setPasswordHash(hash(newPassword));
        user.setMustChangePassword(false);
        return userRepository.save(user);
    }

    /** Người học mới (ROLE_USER) - tên đăng nhập và email chưa ai dùng. */
    private User newLearner(String username, String email, String password) {
        if (userRepository.existsByUsername(username)) {
            throw new BadRequestException("Tên đăng nhập đã tồn tại");
        }
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Email đã được sử dụng");
        }
        return User.builder()
                .username(username)
                .email(email)
                .passwordHash(hash(password))
                .role("ROLE_USER")
                .createdAt(LocalDateTime.now(clock))
                .build();
    }

    static String temporaryPassword() {
        StringBuilder password = new StringBuilder(TEMPORARY_PASSWORD_LENGTH);
        for (int i = 0; i < TEMPORARY_PASSWORD_LENGTH; i++) {
            password.append(TEMPORARY_PASSWORD_ALPHABET.charAt(RANDOM.nextInt(TEMPORARY_PASSWORD_ALPHABET.length())));
        }
        return password.toString();
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
