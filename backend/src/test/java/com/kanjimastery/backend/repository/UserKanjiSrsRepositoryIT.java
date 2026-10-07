package com.kanjimastery.backend.repository;

import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.model.User;
import com.kanjimastery.backend.model.UserKanjiSrs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Các truy vấn native của user_kanji_srs chạy trên PostgreSQL thật. */
class UserKanjiSrsRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private UserKanjiSrsRepository srsRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private KanjiRepository kanjiRepository;
    @Autowired
    private TransactionTemplate transaction;

    private Long userId;
    private Long kanjiId;

    @BeforeEach
    void setUp() {
        String suffix = String.valueOf(System.nanoTime());
        userId = userRepository.save(User.builder()
                .username("srs_" + suffix)
                .email("srs_" + suffix + "@test.local")
                .passwordHash("x")
                .build()).getId();
        kanjiId = kanjiRepository.save(Kanji.builder()
                .character("験" + suffix.substring(suffix.length() - 4))
                .hanViet("NGHIỆM")
                .strokeCount(18)
                .jlptLevel(JlptLevel.N4)
                .meaning("Thử nghiệm")
                .build()).getId();
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(userId);
        kanjiRepository.deleteById(kanjiId);
    }

    /** Hai lần chấm cùng đọc thẻ ở phiên bản cũ: lần ghi sau thất bại thay vì ghi đè lần trước. */
    @Test
    void save_shouldRejectAWriteBasedOnAStaleCard() {
        transaction.executeWithoutResult(status ->
                srsRepository.insertSeenCardIfAbsent(userId, kanjiId, LocalDateTime.now()));
        UserKanjiSrs firstTab = srsRepository.findByUserIdAndKanjiId(userId, kanjiId).orElseThrow();
        UserKanjiSrs secondTab = srsRepository.findByUserIdAndKanjiId(userId, kanjiId).orElseThrow();
        assertThat(firstTab.getVersion()).isZero();

        firstTab.setReviewIntervalDays(3);
        srsRepository.save(firstTab);
        secondTab.setReviewIntervalDays(1);

        assertThatThrownBy(() -> srsRepository.save(secondTab))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(srsRepository.findByUserIdAndKanjiId(userId, kanjiId).orElseThrow())
                .extracting(UserKanjiSrs::getReviewIntervalDays, UserKanjiSrs::getVersion).containsExactly(3, 1L);
        srsRepository.delete(srsRepository.findByUserIdAndKanjiId(userId, kanjiId).orElseThrow());
    }

    @Test
    @Transactional
    void insertSeenCardIfAbsent_shouldAddASeenCardDueNow_andLeaveAnExistingCardAlone() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        assertThat(srsRepository.insertSeenCardIfAbsent(userId, kanjiId, now)).isEqualTo(1);
        assertThat(srsRepository.insertSeenCardIfAbsent(userId, kanjiId, now.plusDays(1))).isZero();

        UserKanjiSrs card = srsRepository.findByUserIdAndKanjiId(userId, kanjiId).orElseThrow();
        assertThat(card.getNextReviewAt()).isEqualTo(now);
        assertThat(card.getLastReviewedAt()).isEqualTo(now);
        assertThat(card.getRepetitionCount()).isZero();
        assertThat(card.getEasinessFactor()).isEqualByComparingTo("2.50");
        assertThat(card.getLapseCount()).isZero();
        // Chưa ôn bằng FSRS: hai cột FSRS null nên Hibernate nạp trạng thái FSRS thành null.
        assertThat(card.getFsrs()).isNull();
        assertThat(card.getStability()).isNull();
    }
}
