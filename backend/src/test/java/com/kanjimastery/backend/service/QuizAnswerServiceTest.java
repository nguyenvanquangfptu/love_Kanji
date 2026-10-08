package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.QuizDirection;
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
import java.util.List;
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

        assertThatThrownBy(() -> quizAnswerService.submit("taro", request(1L, QuizDirection.KANJI_TO_READING, "あく", 2_000)))
                .isInstanceOf(TooManyRequestsException.class);
        verifyNoInteractions(kanjiRepository, srsService);
    }

    @Test
    void submit_shouldGradeOnServerAndRateByResponseTime_whenAnswerIsCorrect() {
        givenAllowed();
        when(kanjiRepository.findById(1L)).thenReturn(Optional.of(aku));
        when(responseTimeRater.rateCorrectAnswer(USER_ID, QuizDirection.KANJI_TO_READING, 2_000)).thenReturn(ReviewRating.EASY);
        LocalDateTime next = LocalDateTime.now().plusDays(6);
        when(srsService.recordQuizAnswer(eq(USER_ID), eq(1L), any())).thenReturn(Optional.of(next));

        QuizAnswerResponse response = quizAnswerService.submit("taro", request(1L, QuizDirection.KANJI_TO_READING, "あく", 2_000));

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
        QuizAnswerResponse response = quizAnswerService.submit("taro", request(1L, QuizDirection.KANJI_TO_READING, "ひらく", 1_500));

        assertThat(response.isCorrect()).isFalse();
        assertThat(response.isInReview()).isTrue();
        verify(responseTimeRater, never()).rateCorrectAnswer(anyLong(), any(), anyInt());
        SrsService.Answer answer = recordedAnswer();
        assertThat(answer.rating()).isEqualTo(ReviewRating.AGAIN);
        assertThat(answer.chosenAnswer()).isEqualTo("ひらく");
    }

    @Test
    void submit_shouldCompareWithSpellingAndMeaning_forOtherDirections() {
        givenAllowed();
        when(kanjiRepository.findById(1L)).thenReturn(Optional.of(aku));
        when(srsService.recordQuizAnswer(eq(USER_ID), eq(1L), any())).thenReturn(Optional.empty());

        assertThat(quizAnswerService.submit("taro", request(1L, QuizDirection.READING_TO_KANJI, "開く", null)).isCorrect()).isTrue();
        assertThat(quizAnswerService.submit("taro", request(1L, QuizDirection.READING_TO_KANJI, "閉く", null)).isCorrect()).isFalse();
        assertThat(quizAnswerService.submit("taro", request(1L, QuizDirection.MEANING, "Mở (cửa)", null)).isCorrect()).isTrue();
    }

    @Test
    void submit_shouldDropImplausibleResponseTime() {
        givenAllowed();
        when(kanjiRepository.findById(1L)).thenReturn(Optional.of(aku));
        when(responseTimeRater.rateCorrectAnswer(USER_ID, QuizDirection.KANJI_TO_READING, null)).thenReturn(ReviewRating.GOOD);
        when(srsService.recordQuizAnswer(eq(USER_ID), eq(1L), any())).thenReturn(Optional.empty());

        QuizAnswerResponse response = quizAnswerService.submit("taro", request(1L, QuizDirection.KANJI_TO_READING, "あく", 600_000));

        assertThat(response.isInReview()).isFalse();
        assertThat(response.getNextReviewAt()).isNull();
        assertThat(recordedAnswer().responseMs()).isNull();
    }

    @Test
    void submit_shouldRejectReadingQuestion_forWordWithoutKanji() {
        when(rateLimiter.tryAcquire(anyString(), anyInt(), any())).thenReturn(true);
        when(kanjiRepository.findById(2L)).thenReturn(Optional.of(katakanaWord));

        assertThatThrownBy(() -> quizAnswerService.submit("taro", request(2L, QuizDirection.KANJI_TO_READING, "テレビ", 1_000)))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(srsService);
    }

    @Test
    void submitTyped_shouldAcceptTheReadingOfAnyEntrySpelledTheSame_andLogTheKanaUnderstood() {
        givenAllowed();
        when(kanjiRepository.findById(1L)).thenReturn(Optional.of(aku));
        // Câu gõ không hiện nghĩa nên không biết đang hỏi dòng 開く nào: cách đọc của dòng nào cũng đúng.
        when(kanjiRepository.findAllByCharacter("開く")).thenReturn(List.of(aku, word(3L, "開く", "ひらく", "Mở ra")));
        when(responseTimeRater.rateCorrectAnswer(USER_ID, QuizDirection.TYPE_READING, 4_000)).thenReturn(ReviewRating.GOOD);
        when(srsService.recordQuizAnswer(eq(USER_ID), eq(1L), any())).thenReturn(Optional.empty());

        QuizAnswerResponse response = quizAnswerService.submit("taro", request(1L, QuizDirection.TYPE_READING, "hiraku", 4_000));

        assertThat(response.isCorrect()).isTrue();
        assertThat(response.getTypedKana()).isEqualTo("ひらく");
        assertThat(response.getCorrectAnswer()).isEqualTo("あく");
        assertThat(response.getMeaning()).isEqualTo("Mở (cửa)");
        SrsService.Answer answer = recordedAnswer();
        assertThat(answer.direction()).isEqualTo(QuizDirection.TYPE_READING);
        assertThat(answer.rating()).isEqualTo(ReviewRating.GOOD);
        assertThat(answer.chosenAnswer()).isEqualTo("ひらく");
    }

    @Test
    void submitTyped_shouldCountANearMissAsForgotten_andNameTheMistake() {
        givenAllowed();
        Kanji shusshin = word(4L, "出身", "しゅっしん", "Xuất thân");
        when(kanjiRepository.findById(4L)).thenReturn(Optional.of(shusshin));
        when(kanjiRepository.findAllByCharacter("出身")).thenReturn(List.of(shusshin));
        when(srsService.recordQuizAnswer(eq(USER_ID), eq(4L), any())).thenReturn(Optional.of(LocalDateTime.now()));

        QuizAnswerResponse response = quizAnswerService.submit("taro", request(4L, QuizDirection.TYPE_READING, "shushin", 3_000));

        assertThat(response.isCorrect()).isFalse();
        assertThat(response.getMistake()).isEqualTo(ReadingMatcher.Mistake.SOKUON);
        assertThat(response.getTypedKana()).isEqualTo("しゅしん");
        assertThat(recordedAnswer().rating()).isEqualTo(ReviewRating.AGAIN);
        verify(responseTimeRater, never()).rateCorrectAnswer(anyLong(), any(), anyInt());
    }

    @Test
    void submitTyped_shouldCountGivingUpAsForgotten_withoutAnyTypedAnswer() {
        givenAllowed();
        when(kanjiRepository.findById(1L)).thenReturn(Optional.of(aku));
        when(srsService.recordQuizAnswer(eq(USER_ID), eq(1L), any())).thenReturn(Optional.of(LocalDateTime.now()));
        QuizAnswerRequest gaveUp = request(1L, QuizDirection.TYPE_READING, null, 9_000);
        gaveUp.setGaveUp(true);

        QuizAnswerResponse response = quizAnswerService.submit("taro", gaveUp);

        assertThat(response.isCorrect()).isFalse();
        assertThat(response.getTypedKana()).isNull();
        assertThat(response.getCorrectAnswer()).isEqualTo("あく");
        SrsService.Answer answer = recordedAnswer();
        assertThat(answer.rating()).isEqualTo(ReviewRating.AGAIN);
        assertThat(answer.chosenAnswer()).isNull();
    }

    @Test
    void submitTyped_shouldRefuseUnreadableInput_andWordsWithoutKanji_withoutLoggingAnything() {
        when(rateLimiter.tryAcquire(anyString(), anyInt(), any())).thenReturn(true);
        when(kanjiRepository.findById(1L)).thenReturn(Optional.of(aku));
        when(kanjiRepository.findAllByCharacter("開く")).thenReturn(List.of(aku));
        when(kanjiRepository.findById(2L)).thenReturn(Optional.of(katakanaWord));

        assertThatThrownBy(() -> quizAnswerService.submit("taro", request(1L, QuizDirection.TYPE_READING, "aku2", 2_000)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("romaji");
        assertThatThrownBy(() -> quizAnswerService.submit("taro", request(2L, QuizDirection.TYPE_READING, "terebi", 2_000)))
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

    private static QuizAnswerRequest request(Long kanjiId, QuizDirection direction, String chosenAnswer, Integer responseMs) {
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
