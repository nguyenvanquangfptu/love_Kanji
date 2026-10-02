package com.kanjimastery.backend.service;

import com.kanjimastery.backend.config.RateLimitProperties;
import com.kanjimastery.backend.dto.QuizAnswerRequest;
import com.kanjimastery.backend.dto.QuizAnswerResponse;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.exception.TooManyRequestsException;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.model.ReviewRating;
import com.kanjimastery.backend.model.ReviewSource;
import com.kanjimastery.backend.model.User;
import com.kanjimastery.backend.repository.KanjiRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuizAnswerServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private KanjiRepository kanjiRepository;
    @Mock
    private UserService userService;
    @Mock
    private SrsService srsService;
    @Mock
    private ResponseTimeRater responseTimeRater;
    @Mock
    private RateLimiterService rateLimiter;

    private QuizAnswerService quizAnswerService;

    // Cùng viết 開く nhưng là hai dòng khác nghĩa (V9): chấm theo đúng kanjiId được hỏi.
    private final Kanji aku = word(1L, "開く", "あく", "Mở (cửa)");
    private final Kanji katakanaWord = word(2L, "テレビ", null, "Ti vi");

    @BeforeEach
    void setUp() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.getQuizAnswer().setLimit(120);
        properties.getQuizAnswer().setWindowSeconds(60);
        quizAnswerService = new QuizAnswerService(kanjiRepository, userService, srsService, responseTimeRater,
                rateLimiter, properties);
    }

    @Test
    void submit_shouldRejectRequest_whenUserExceedsAnswerLimit() {
        when(rateLimiter.tryAcquire("ratelimit:quiz-answer:taro", 120, Duration.ofSeconds(60))).thenReturn(false);

        assertThatThrownBy(() -> quizAnswerService.submit("taro", request(1L, "KANJI_TO_READING", "あく", 2_000)))
                .isInstanceOf(TooManyRequestsException.class);
        verifyNoInteractions(kanjiRepository, srsService);
    }

    @Test
    void submit_shouldGradeOnServerAndRateByResponseTime_whenAnswerIsCorrect() {
        givenAllowed();
        when(kanjiRepository.findById(1L)).thenReturn(Optional.of(aku));
        when(responseTimeRater.rateCorrectAnswer(USER_ID, "KANJI_TO_READING", 2_000)).thenReturn(ReviewRating.EASY);
        LocalDateTime next = LocalDateTime.now().plusDays(6);
        when(srsService.recordQuizAnswer(eq(USER_ID), eq(1L), any())).thenReturn(Optional.of(next));

        QuizAnswerResponse response = quizAnswerService.submit("taro", request(1L, "KANJI_TO_READING", "あく", 2_000));

        assertThat(response.isCorrect()).isTrue();
        assertThat(response.isInReview()).isTrue();
        assertThat(response.getNextReviewAt()).isEqualTo(next);
        SrsService.Answer answer = recordedAnswer();
        assertThat(answer.source()).isEqualTo(ReviewSource.QUIZ);
        assertThat(answer.correct()).isTrue();
        assertThat(answer.rating()).isEqualTo(ReviewRating.EASY);
        assertThat(answer.responseMs()).isEqualTo(2_000);
    }

    @Test
    void submit_shouldRateAgainWithoutTimingIt_whenAnswerIsWrong() {
        givenAllowed();
        when(kanjiRepository.findById(1L)).thenReturn(Optional.of(aku));
        when(srsService.recordQuizAnswer(eq(USER_ID), eq(1L), any())).thenReturn(Optional.of(LocalDateTime.now()));

        // ひらく là cách đọc của một dòng 開く khác - với dòng đang hỏi thì vẫn là sai.
        QuizAnswerResponse response = quizAnswerService.submit("taro", request(1L, "KANJI_TO_READING", "ひらく", 1_500));

        assertThat(response.isCorrect()).isFalse();
        assertThat(response.isInReview()).isTrue();
        verify(responseTimeRater, never()).rateCorrectAnswer(anyLong(), anyString(), anyInt());
        SrsService.Answer answer = recordedAnswer();
        assertThat(answer.rating()).isEqualTo(ReviewRating.AGAIN);
        assertThat(answer.chosenAnswer()).isEqualTo("ひらく");
    }

    @Test
    void submit_shouldCompareWithSpellingAndMeaning_forOtherDirections() {
        givenAllowed();
        when(kanjiRepository.findById(1L)).thenReturn(Optional.of(aku));
        when(srsService.recordQuizAnswer(eq(USER_ID), eq(1L), any())).thenReturn(Optional.empty());

        assertThat(quizAnswerService.submit("taro", request(1L, "READING_TO_KANJI", "開く", null)).isCorrect()).isTrue();
        assertThat(quizAnswerService.submit("taro", request(1L, "READING_TO_KANJI", "閉く", null)).isCorrect()).isFalse();
        assertThat(quizAnswerService.submit("taro", request(1L, "MEANING", "Mở (cửa)", null)).isCorrect()).isTrue();
    }

    @Test
    void submit_shouldDropImplausibleResponseTime() {
        givenAllowed();
        when(kanjiRepository.findById(1L)).thenReturn(Optional.of(aku));
        when(responseTimeRater.rateCorrectAnswer(USER_ID, "KANJI_TO_READING", null)).thenReturn(ReviewRating.GOOD);
        when(srsService.recordQuizAnswer(eq(USER_ID), eq(1L), any())).thenReturn(Optional.empty());

        QuizAnswerResponse response = quizAnswerService.submit("taro", request(1L, "KANJI_TO_READING", "あく", 600_000));

        assertThat(response.isInReview()).isFalse();
        assertThat(response.getNextReviewAt()).isNull();
        assertThat(recordedAnswer().responseMs()).isNull();
    }

    @Test
    void submit_shouldRejectReadingQuestion_forWordWithoutKanji() {
        when(rateLimiter.tryAcquire(anyString(), anyInt(), any())).thenReturn(true);
        when(kanjiRepository.findById(2L)).thenReturn(Optional.of(katakanaWord));

        assertThatThrownBy(() -> quizAnswerService.submit("taro", request(2L, "KANJI_TO_READING", "テレビ", 1_000)))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(srsService);
    }

    private void givenAllowed() {
        when(rateLimiter.tryAcquire(anyString(), anyInt(), any())).thenReturn(true);
        User user = new User();
        user.setId(USER_ID);
        when(userService.getByUsername("taro")).thenReturn(user);
    }

    private SrsService.Answer recordedAnswer() {
        ArgumentCaptor<SrsService.Answer> captor = ArgumentCaptor.forClass(SrsService.Answer.class);
        verify(srsService).recordQuizAnswer(eq(USER_ID), anyLong(), captor.capture());
        return captor.getValue();
    }

    private static QuizAnswerRequest request(Long kanjiId, String direction, String chosenAnswer, Integer responseMs) {
        QuizAnswerRequest request = new QuizAnswerRequest();
        request.setKanjiId(kanjiId);
        request.setDirection(direction);
        request.setChosenAnswer(chosenAnswer);
        request.setResponseMs(responseMs);
        return request;
    }

    private static Kanji word(Long id, String character, String reading, String meaning) {
        return Kanji.builder().id(id).character(character).reading(reading).meaning(meaning).build();
    }
}
