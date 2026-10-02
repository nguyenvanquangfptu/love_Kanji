package com.kanjimastery.backend.service;

import com.kanjimastery.backend.dto.AddSrsCardsResponse;
import com.kanjimastery.backend.dto.ReviewRequest;
import com.kanjimastery.backend.dto.SrsTagStatusResponse;
import com.kanjimastery.backend.model.CardState;
import com.kanjimastery.backend.model.ReviewLog;
import com.kanjimastery.backend.model.ReviewRating;
import com.kanjimastery.backend.model.ReviewSource;
import com.kanjimastery.backend.model.UserKanjiSrs;
import com.kanjimastery.backend.repository.KanjiRepository;
import com.kanjimastery.backend.repository.ReviewLogRepository;
import com.kanjimastery.backend.repository.UserKanjiSrsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SrsServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long KANJI_ID = 42L;

    @Mock
    private UserKanjiSrsRepository srsRepository;

    @Mock
    private KanjiRepository kanjiRepository;

    @Mock
    private ReviewLogRepository reviewLogRepository;

    @Spy
    private SrsCalculatorService srsCalculatorService = new SrsCalculatorService();

    @InjectMocks
    private SrsService srsService;

    @Test
    void addCards_shouldDeduplicateIdsAndReportAlreadyInReview() {
        Set<Long> uniqueIds = Set.of(1L, 2L, 3L, 999L);
        // id 999 không tồn tại -> chỉ 3 từ hợp lệ; trong đó 1 từ đã có trong lịch ôn nên chỉ thêm được 2.
        when(kanjiRepository.countByIdIn(uniqueIds)).thenReturn(3L);
        when(srsRepository.insertCardsIfAbsent(eq(7L), eq(uniqueIds), any())).thenReturn(2);

        AddSrsCardsResponse result = srsService.addCards(7L, List.of(1L, 2L, 2L, 3L, 999L));

        assertThat(result.getAdded()).isEqualTo(2);
        assertThat(result.getAlreadyInReview()).isEqualTo(1);
        verify(srsRepository).insertCardsIfAbsent(eq(7L), eq(uniqueIds), any());
    }

    @Test
    void addCards_shouldReportAllExisting_whenEveryWordIsAlreadyInReview() {
        when(kanjiRepository.countByIdIn(Set.of(5L, 6L))).thenReturn(2L);
        when(srsRepository.insertCardsIfAbsent(eq(7L), eq(Set.of(5L, 6L)), any())).thenReturn(0);

        AddSrsCardsResponse result = srsService.addCards(7L, List.of(5L, 6L));

        assertThat(result.getAdded()).isZero();
        assertThat(result.getAlreadyInReview()).isEqualTo(2);
    }

    @Test
    void getTagStatus_shouldReturnWordCountAndCardsInReview() {
        when(kanjiRepository.countByTags_Id(4L)).thenReturn(43L);
        when(srsRepository.countInReviewByTag(7L, 4L)).thenReturn(12L);

        SrsTagStatusResponse result = srsService.getTagStatus(7L, 4L);

        assertThat(result.getTotalWords()).isEqualTo(43);
        assertThat(result.getInReview()).isEqualTo(12);
    }

    @Test
    void submitReview_shouldScheduleFirstReviewAndLogItAsNewCard() {
        when(kanjiRepository.existsById(KANJI_ID)).thenReturn(true);
        when(srsRepository.findByUserIdAndKanjiId(USER_ID, KANJI_ID)).thenReturn(Optional.empty());
        givenSaveReturnsCard();

        srsService.submitReview(USER_ID, reviewRequest(ReviewRating.GOOD, 70_000));

        UserKanjiSrs saved = savedCard();
        assertThat(saved.getRepetitionCount()).isEqualTo(1);
        assertThat(saved.getReviewIntervalDays()).isEqualTo(1);

        ReviewLog log = savedLog();
        assertThat(log.getSource()).isEqualTo(ReviewSource.FLASHCARD);
        assertThat(log.getDirection()).isNull();
        assertThat(log.getCorrect()).isTrue();
        assertThat(log.getRating()).isEqualTo((short) ReviewRating.GOOD);
        assertThat(log.getStateBefore()).isEqualTo(CardState.NEW);
        assertThat(log.getEfBefore()).isNull();
        assertThat(log.getIntervalBefore()).isNull();
        assertThat(log.getScheduled()).isTrue();
        // 70 giây là đã rời máy, không phải thời gian nhớ lại.
        assertThat(log.getResponseMs()).isNull();
    }

    @Test
    void submitReview_shouldResetCardOnAgainAndLogStateBeforeTheLapse() {
        UserKanjiSrs card = card(3, "2.50", 15, LocalDateTime.now().minusHours(1));
        when(kanjiRepository.existsById(KANJI_ID)).thenReturn(true);
        when(srsRepository.findByUserIdAndKanjiId(USER_ID, KANJI_ID)).thenReturn(Optional.of(card));
        givenSaveReturnsCard();

        srsService.submitReview(USER_ID, reviewRequest(ReviewRating.AGAIN, 2_500));

        assertThat(card.getRepetitionCount()).isZero();
        assertThat(card.getReviewIntervalDays()).isEqualTo(1);

        ReviewLog log = savedLog();
        assertThat(log.getCorrect()).isFalse();
        assertThat(log.getStateBefore()).isEqualTo(CardState.REVIEW);
        assertThat(log.getEfBefore()).isEqualByComparingTo("2.50");
        assertThat(log.getIntervalBefore()).isEqualTo(15);
        assertThat(log.getResponseMs()).isEqualTo(2_500);
    }

    @Test
    void submitReview_shouldLogRelearning_whenCardWasForgottenLastTime() {
        UserKanjiSrs card = card(0, "2.18", 1, LocalDateTime.now().minusMinutes(5));
        when(kanjiRepository.existsById(KANJI_ID)).thenReturn(true);
        when(srsRepository.findByUserIdAndKanjiId(USER_ID, KANJI_ID)).thenReturn(Optional.of(card));
        givenSaveReturnsCard();

        srsService.submitReview(USER_ID, reviewRequest(ReviewRating.HARD, null));

        assertThat(savedLog().getStateBefore()).isEqualTo(CardState.RELEARNING);
    }

    @Test
    void recordQuizAnswer_shouldAddWordDueNow_whenWrongAndNotInReview() {
        when(srsRepository.findByUserIdAndKanjiId(USER_ID, KANJI_ID)).thenReturn(Optional.empty());

        Optional<LocalDateTime> next = srsService.recordQuizAnswer(USER_ID, KANJI_ID, quizAnswer(false, ReviewRating.AGAIN));

        assertThat(next).isPresent();
        verify(srsRepository).insertCardsIfAbsent(USER_ID, Set.of(KANJI_ID), next.get());
        verify(srsRepository, never()).save(any());
        ReviewLog log = savedLog();
        assertThat(log.getScheduled()).isFalse();
        assertThat(log.getStateBefore()).isEqualTo(CardState.NEW);
        assertThat(log.getChosenAnswer()).isEqualTo("あく");
    }

    @Test
    void recordQuizAnswer_shouldOnlyLog_whenCorrectAndNotInReview() {
        when(srsRepository.findByUserIdAndKanjiId(USER_ID, KANJI_ID)).thenReturn(Optional.empty());

        Optional<LocalDateTime> next = srsService.recordQuizAnswer(USER_ID, KANJI_ID, quizAnswer(true, ReviewRating.EASY));

        assertThat(next).isEmpty();
        verify(srsRepository, never()).insertCardsIfAbsent(anyLong(), any(), any());
        verify(srsRepository, never()).save(any());
        assertThat(savedLog().getScheduled()).isFalse();
    }

    @Test
    void recordQuizAnswer_shouldRelearnWord_whenWrongEvenIfNotDue() {
        UserKanjiSrs card = card(4, "2.60", 20, LocalDateTime.now().plusDays(10));
        when(srsRepository.findByUserIdAndKanjiId(USER_ID, KANJI_ID)).thenReturn(Optional.of(card));
        givenSaveReturnsCard();

        srsService.recordQuizAnswer(USER_ID, KANJI_ID, quizAnswer(false, ReviewRating.AGAIN));

        assertThat(card.getRepetitionCount()).isZero();
        assertThat(card.getReviewIntervalDays()).isEqualTo(1);
        assertThat(savedLog().getScheduled()).isTrue();
    }

    @Test
    void recordQuizAnswer_shouldCountAsReview_whenCorrectAndDue() {
        UserKanjiSrs card = card(2, "2.50", 6, LocalDateTime.now().minusMinutes(1));
        when(srsRepository.findByUserIdAndKanjiId(USER_ID, KANJI_ID)).thenReturn(Optional.of(card));
        givenSaveReturnsCard();

        srsService.recordQuizAnswer(USER_ID, KANJI_ID, quizAnswer(true, ReviewRating.EASY));

        // "Dễ" = SM-2 quality 5: EF +0.1, khoảng ôn = round(6 * 2.6).
        assertThat(card.getEasinessFactor()).isEqualByComparingTo("2.60");
        assertThat(card.getReviewIntervalDays()).isEqualTo(16);
        assertThat(savedLog().getScheduled()).isTrue();
    }

    @Test
    void recordQuizAnswer_shouldKeepSchedule_whenCorrectBeforeDue() {
        LocalDateTime due = LocalDateTime.now().plusDays(3);
        UserKanjiSrs card = card(3, "2.50", 15, due);
        when(srsRepository.findByUserIdAndKanjiId(USER_ID, KANJI_ID)).thenReturn(Optional.of(card));

        Optional<LocalDateTime> next = srsService.recordQuizAnswer(USER_ID, KANJI_ID, quizAnswer(true, ReviewRating.EASY));

        assertThat(next).contains(due);
        assertThat(card.getReviewIntervalDays()).isEqualTo(15);
        verify(srsRepository, never()).save(any());
        ReviewLog log = savedLog();
        assertThat(log.getScheduled()).isFalse();
        assertThat(log.getStateBefore()).isEqualTo(CardState.REVIEW);
    }

    private static ReviewRequest reviewRequest(int rating, Integer responseMs) {
        ReviewRequest request = new ReviewRequest();
        request.setKanjiId(KANJI_ID);
        request.setRating(rating);
        request.setResponseMs(responseMs);
        return request;
    }

    private static SrsService.Answer quizAnswer(boolean correct, int rating) {
        return new SrsService.Answer(ReviewSource.QUIZ, "KANJI_TO_READING", correct, rating, 3_000, "あく");
    }

    /** Thẻ đã ôn ít nhất một lần, lần ôn trước cách {@code nextReviewAt} đúng một khoảng ôn. */
    private static UserKanjiSrs card(int repetitions, String ef, int intervalDays, LocalDateTime nextReviewAt) {
        return UserKanjiSrs.builder()
                .userId(USER_ID)
                .kanjiId(KANJI_ID)
                .repetitionCount(repetitions)
                .easinessFactor(new BigDecimal(ef))
                .reviewIntervalDays(intervalDays)
                .nextReviewAt(nextReviewAt)
                .lastReviewedAt(nextReviewAt.minusDays(intervalDays))
                .build();
    }

    private void givenSaveReturnsCard() {
        when(srsRepository.save(any(UserKanjiSrs.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private UserKanjiSrs savedCard() {
        ArgumentCaptor<UserKanjiSrs> captor = ArgumentCaptor.forClass(UserKanjiSrs.class);
        verify(srsRepository).save(captor.capture());
        return captor.getValue();
    }

    private ReviewLog savedLog() {
        ArgumentCaptor<ReviewLog> captor = ArgumentCaptor.forClass(ReviewLog.class);
        verify(reviewLogRepository).save(captor.capture());
        return captor.getValue();
    }
}
