package com.kanjimastery.backend.service;

import com.kanjimastery.backend.dto.CreateUserRequest;
import com.kanjimastery.backend.dto.CreatedUserResponse;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.kanjimastery.backend.model.User;
import com.kanjimastery.backend.repository.UserRepository;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
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
        assertThat(result.isMustChangePassword()).isFalse();
        verify(userRepository).save(any(User.class));
    }

    // Trùng tên hay trùng email đều cùng một câu: người ngoài không dò được email nào đã có tài khoản.
    private static final String TAKEN_ON_REGISTER = "Tên đăng nhập hoặc email đã được sử dụng";

    @Test
    void register_shouldThrow_whenUsernameAlreadyExists_withoutSayingWhichFieldIsTaken() {
        when(userRepository.existsByUsername("taro")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(registerRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(TAKEN_ON_REGISTER);

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_shouldThrow_whenEmailAlreadyExists_withTheSameMessageAsATakenUsername() {
        when(userRepository.existsByUsername("taro")).thenReturn(false);
        when(userRepository.existsByEmail("taro@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(registerRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(TAKEN_ON_REGISTER);

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
    void createAccount_shouldMakeALearner_withAHashedTemporaryPasswordToChangeAtFirstLogin() {
        when(passwordEncoder.encode(any())).thenReturn("hashed-temporary");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(15L);
            return user;
        });

        CreatedUserResponse created = userService.createAccount(createRequest("hanako", "hanako@example.com"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo("ROLE_USER");
        assertThat(saved.getValue().isMustChangePassword()).isTrue();
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed-temporary");
        // Mật khẩu tạm trả về đúng là mật khẩu đã băm, còn response không có mã băm.
        verify(passwordEncoder).encode(created.temporaryPassword());
        assertThat(created.user().id()).isEqualTo(15L);
        assertThat(created.user().username()).isEqualTo("hanako");
        assertThat(created.user().mustChangePassword()).isTrue();
    }

    @Test
    void createAccount_shouldLogTheNewAccount_butNeverItsTemporaryPassword(CapturedOutput output) {
        when(passwordEncoder.encode(any())).thenReturn("hashed-temporary");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(15L);
            return user;
        });

        CreatedUserResponse created = userService.createAccount(createRequest("hanako", "hanako@example.com"));

        assertThat(output).contains("user=hanako id=15")
                .doesNotContain(created.temporaryPassword(), "hashed-temporary", "hanako@example.com");
    }

    @Test
    void createAccount_shouldRefuseAUsernameAlreadyTaken() {
        when(userRepository.existsByUsername("taro")).thenReturn(true);

        assertThatThrownBy(() -> userService.createAccount(createRequest("taro", "other@example.com")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Tên đăng nhập đã tồn tại");
        verify(userRepository, never()).save(any());
    }

    @Test
    void createAccount_shouldTellTheAdmin_whenTheEmailIsTaken() {
        when(userRepository.existsByUsername("hanako")).thenReturn(false);
        when(userRepository.existsByEmail("taro@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createAccount(createRequest("hanako", "taro@example.com")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Email đã được sử dụng");
        verify(userRepository, never()).save(any());
    }

    @Test
    void temporaryPassword_shouldBe12UnambiguousCharacters_andDifferEachTime() {
        Set<String> passwords = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            String password = UserService.temporaryPassword();
            assertThat(password).hasSize(UserService.TEMPORARY_PASSWORD_LENGTH);
            assertThat(password.chars()).allMatch(c -> UserService.TEMPORARY_PASSWORD_ALPHABET.indexOf(c) >= 0);
            assertThat(password).doesNotContainAnyWhitespaces().doesNotContain("0", "O", "1", "l", "I");
            passwords.add(password);
        }
        assertThat(passwords).hasSize(50);
    }

    @Test
    void setPassword_shouldStoreTheNewHash_andEndTheTemporaryPassword() {
        User existing = User.builder().id(1L).username("taro").passwordHash("old-hash").mustChangePassword(true).build();
        when(passwordEncoder.encode("new-password123")).thenReturn("new-hash");

        userService.setPassword(existing, "new-password123");

        assertThat(existing.getPasswordHash()).isEqualTo("new-hash");
        assertThat(existing.isMustChangePassword()).isFalse();
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

    private static CreateUserRequest createRequest(String username, String email) {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername(username);
        request.setEmail(email);
        return request;
    }
}
