package com.kanjimastery.backend.repository;

import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.model.FsrsParameters;
import com.kanjimastery.backend.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Tham số FSRS lưu dạng JSONB trên PostgreSQL thật. */
class FsrsParametersRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private FsrsParametersRepository parametersRepository;
    @Autowired
    private UserRepository userRepository;

    private Long userId;

    @BeforeEach
    void setUp() {
        String suffix = String.valueOf(System.nanoTime());
        userId = userRepository.save(User.builder()
                .username("fsrs_" + suffix)
                .email("fsrs_" + suffix + "@test.local")
                .passwordHash("x")
                .build()).getId();
    }

    @AfterEach
    void tearDown() {
        // ON DELETE CASCADE dọn luôn tham số của người dùng thử.
        userRepository.deleteById(userId);
    }

    @Test
    void save_shouldKeepAll21ParametersExactly() {
        List<Double> parameters = IntStream.range(0, 21).mapToObj(i -> i + 0.123456789012345).toList();
        LocalDateTime optimizedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        parametersRepository.save(FsrsParameters.builder()
                .userId(userId)
                .fsrsVersion("FSRS-6")
                .parameters(parameters)
                .firstReviews(120)
                .optimizedAt(optimizedAt)
                .build());

        FsrsParameters saved = parametersRepository.findById(userId).orElseThrow();

        assertThat(saved.getParameters()).containsExactlyElementsOf(parameters);
        assertThat(saved.getFsrsVersion()).isEqualTo("FSRS-6");
        assertThat(saved.getFirstReviews()).isEqualTo(120);
        assertThat(saved.getOptimizedAt()).isEqualTo(optimizedAt);
    }
}
