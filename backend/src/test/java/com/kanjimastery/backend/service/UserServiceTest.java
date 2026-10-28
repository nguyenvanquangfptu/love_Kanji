package com.kanjimastery.backend.service;

import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.kanjimastery.backend.model.User;
import com.kanjimastery.backend.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Spy
    private Clock clock = Clock.systemDefaultZone();

    @InjectMocks
    private UserService userService;

    private RegisterRequest registerRequest;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setUsername("taro");
        registerRequest.setEmail("taro@example.com");
        registerRequest.setPassword("password123");
    }

    @Test
    void register_shouldCreateUser_whenUsernameAndEmailAreAvailable() {
        when(userRepository.existsByUsername("taro")).thenReturn(false);
        when(userRepository.existsByEmail("taro@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.register(registerRequest);

        assertThat(result.getUsername()).isEqualTo("taro");
        assertThat(result.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(result.getRole()).isEqualTo("ROLE_USER");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_shouldThrow_whenUsernameAlreadyExists() {
        when(userRepository.existsByUsername("taro")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(registerRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Tên đăng nhập");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_shouldThrow_whenEmailAlreadyExists() {
        when(userRepository.existsByUsername("taro")).thenReturn(false);
        when(userRepository.existsByEmail("taro@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(registerRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Email");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_shouldAcceptA72BytePassword() {
        registerRequest.setPassword("a".repeat(72));
        when(passwordEncoder.encode("a".repeat(72))).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(userService.register(registerRequest).getPasswordHash()).isEqualTo("hashed-password");
    }

    @Test
    void register_shouldRefuseAPasswordLongerThan72Bytes_countingDiacriticsAsSeveralBytes() {
        // 25 letters, but "ệ" takes 3 bytes in UTF-8: 75 bytes.
        registerRequest.setPassword("ệ".repeat(25));

        assertThatThrownBy(() -> userService.register(registerRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("72 byte");
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void setPassword_shouldStoreTheNewHash() {
        User existing = User.builder().id(1L).username("taro").passwordHash("old-hash").build();
        when(passwordEncoder.encode("new-password123")).thenReturn("new-hash");

        userService.setPassword(existing, "new-password123");

        assertThat(existing.getPasswordHash()).isEqualTo("new-hash");
        verify(userRepository).save(existing);
    }

    @Test
    void setPassword_shouldRefuseANewPasswordLongerThan72Bytes() {
        User existing = User.builder().id(1L).username("taro").passwordHash("old-hash").build();

        assertThatThrownBy(() -> userService.setPassword(existing, "a".repeat(73)))
                .isInstanceOf(BadRequestException.class);
        assertThat(existing.getPasswordHash()).isEqualTo("old-hash");
        verify(userRepository, never()).save(any());
    }

    @Test
    void passwordMatches_shouldCompareWithTheStoredHash() {
        User existing = User.builder().id(1L).username("taro").passwordHash("old-hash").build();
        when(passwordEncoder.matches("old-password", "old-hash")).thenReturn(true);
        when(passwordEncoder.matches("wrong-password", "old-hash")).thenReturn(false);

        assertThat(userService.passwordMatches(existing, "old-password")).isTrue();
        assertThat(userService.passwordMatches(existing, "wrong-password")).isFalse();
    }
}
