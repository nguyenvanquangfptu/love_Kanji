package com.kanjimastery.backend.repository;

import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.model.CardState;
import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.model.QuizDirection;
import com.kanjimastery.backend.model.ReviewLog;
import com.kanjimastery.backend.model.ReviewRating;
import com.kanjimastery.backend.model.ReviewSource;
import com.kanjimastery.backend.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Mọi hướng hỏi trong {@link QuizDirection} đều ghi được vào nhật ký ôn tập (ck_review_logs_direction, V43). */
class ReviewDirectionsIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private KanjiRepository kanjiRepository;
    @Autowired
    private ReviewLogRepository reviewLogRepository;

    private Long userId;
    private Long wordId;

    @BeforeEach
    void setUp() {
        String suffix = String.valueOf(System.nanoTime());
        userId = userRepository.save(User.builder().username("dir_" + suffix).email("dir_" + suffix + "@test.local")
                .passwordHash("x").build()).getId();
        wordId = kanjiRepository.save(Kanji.builder().character("鬱").hanViet("").meaning("IT").strokeCount(1)
                .jlptLevel(JlptLevel.N3).build()).getId();
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(userId);
        kanjiRepository.deleteById(wordId);
    }

    @ParameterizedTest
    @EnumSource(QuizDirection.class)
    void everyDirection_shouldBeAcceptedByTheDatabase(QuizDirection direction) {
        ReviewSource source = direction == QuizDirection.RECALL_WORD ? ReviewSource.FLASHCARD : ReviewSource.QUIZ;
        ReviewLog saved = reviewLogRepository.saveAndFlush(ReviewLog.builder().userId(userId).kanjiId(wordId)
                .source(source).direction(direction).correct(true).rating(ReviewRating.GOOD)
                .stateBefore(CardState.REVIEW).scheduled(true).reviewedAt(LocalDateTime.now()).build());

        assertThat(reviewLogRepository.findById(saved.getId())).get().extracting(ReviewLog::getDirection)
                .isEqualTo(direction);
    }
}
