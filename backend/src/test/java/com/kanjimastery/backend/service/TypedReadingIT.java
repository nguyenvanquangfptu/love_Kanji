package com.kanjimastery.backend.service;

import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.dto.QuizAnswerRequest;
import com.kanjimastery.backend.dto.QuizAnswerResponse;
import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.model.QuizDirection;
import com.kanjimastery.backend.model.ReviewLog;
import com.kanjimastery.backend.model.ReviewRating;
import com.kanjimastery.backend.model.User;
import com.kanjimastery.backend.repository.KanjiRepository;
import com.kanjimastery.backend.repository.ReviewLogRepository;
import com.kanjimastery.backend.repository.UserKanjiSrsRepository;
import com.kanjimastery.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** Gõ cách đọc trên PostgreSQL + Redis thật: chấm ở server, từ gõ sai vào lịch ôn, nhật ký ghi hướng gõ (V31). */
class TypedReadingIT extends AbstractIntegrationTest {

    @Autowired
    private QuizAnswerService quizAnswerService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private KanjiRepository kanjiRepository;
    @Autowired
    private UserKanjiSrsRepository srsRepository;
    @Autowired
    private ReviewLogRepository reviewLogRepository;

    private User user;
    private Kanji word;

    @BeforeEach
    void setUp() {
        String suffix = String.valueOf(System.nanoTime());
        user = userRepository.save(User.builder().username("typing_" + suffix).email("typing_" + suffix + "@test.local")
                .passwordHash("x").build());
        word = kanjiRepository.save(Kanji.builder().character("出身" + suffix.substring(suffix.length() - 3))
                .reading("しゅっしん").hanViet("XUẤT THÂN").strokeCount(12).jlptLevel(JlptLevel.N4).meaning("Xuất thân")
                .build());
    }

    @AfterEach
    void tearDown() {
        reviewLogRepository.deleteAll(logs());
        srsRepository.findByUserIdAndKanjiId(user.getId(), word.getId()).ifPresent(srsRepository::delete);
        kanjiRepository.delete(word);
        userRepository.delete(user);
    }

    @Test
    void wrongTypedReading_shouldPutTheWordIntoReview_andLogTheKanaUnderstood() {
        QuizAnswerResponse wrong = quizAnswerService.submit(user.getUsername(), typed("shushin"));

        assertThat(wrong.isCorrect()).isFalse();
        assertThat(wrong.getMistake()).isEqualTo(ReadingMatcher.Mistake.SOKUON);
        assertThat(wrong.isInReview()).isTrue();
        assertThat(srsRepository.findByUserIdAndKanjiId(user.getId(), word.getId())).isPresent();

        QuizAnswerResponse right = quizAnswerService.submit(user.getUsername(), typed("shusshin"));

        assertThat(right.isCorrect()).isTrue();
        assertThat(logs()).extracting(ReviewLog::getDirection, ReviewLog::getChosenAnswer, ReviewLog::getRating)
                .containsExactlyInAnyOrder(
                        tuple(QuizDirection.TYPE_READING, "しゅしん", ReviewRating.AGAIN),
                        tuple(QuizDirection.TYPE_READING, "しゅっしん", ReviewRating.GOOD));
    }

    private List<ReviewLog> logs() {
        return reviewLogRepository.findAll().stream()
                .filter(log -> log.getUserId().equals(user.getId()))
                .toList();
    }

    private QuizAnswerRequest typed(String reading) {
        QuizAnswerRequest request = new QuizAnswerRequest();
        request.setKanjiId(word.getId());
        request.setDirection(QuizDirection.TYPE_READING);
        request.setChosenAnswer(reading);
        return request;
    }
}
