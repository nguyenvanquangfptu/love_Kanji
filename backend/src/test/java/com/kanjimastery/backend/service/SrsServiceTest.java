package com.kanjimastery.backend.service;

import com.kanjimastery.backend.config.SrsProperties;
import com.kanjimastery.backend.dto.AddSrsCardsResponse;
import com.kanjimastery.backend.dto.DailyCardResponse;
import com.kanjimastery.backend.dto.DailyPlanResponse;
import com.kanjimastery.backend.dto.HardWordsResponse;
import com.kanjimastery.backend.dto.ReviewRequest;
import com.kanjimastery.backend.dto.SrsTagStatusResponse;
import com.kanjimastery.backend.exception.ResourceNotFoundException;
import com.kanjimastery.backend.model.CardState;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.model.ReviewLog;
import com.kanjimastery.backend.model.ReviewRating;
import com.kanjimastery.backend.model.ReviewSource;
import com.kanjimastery.backend.model.SchedulerType;
import com.kanjimastery.backend.model.UserKanjiSrs;
import com.kanjimastery.backend.repository.KanjiRepository;
import com.kanjimastery.backend.repository.ReviewLogRepository;
import com.kanjimastery.backend.repository.UserKanjiSrsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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

    @Spy
    private SrsProperties srsProperties = new SrsProperties();

    @Mock
    private StudyPlanService studyPlanService;

    @Spy
    private StudyCalendar calendar = new StudyCalendar(new SrsProperties());

    @Mock
    private LearningProfileService learningProfileService;

    @InjectMocks
    private SrsService srsService;

    @BeforeEach
    void scheduleWithSm2ByDefault() {
        lenient().when(learningProfileService.scheduling(anyLong())).thenReturn(SchedulingSettings.DEFAULT);
    }

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

        assertThat(card.getLapseCount()).isEqualTo(1);

        ReviewLog log = savedLog();
        assertThat(log.getCorrect()).isFalse();
        assertThat(log.getStateBefore()).isEqualTo(CardState.REVIEW);
        assertThat(log.getEfBefore()).isEqualByComparingTo("2.50");
        assertThat(log.getIntervalBefore()).isEqualTo(15);
        assertThat(log.getResponseMs()).isEqualTo(2_500);
    }

    @Test
    void submitReview_shouldStartTheFsrsMemory_onAWordsFirstReview() {
        when(kanjiRepository.existsById(KANJI_ID)).thenReturn(true);
        when(srsRepository.findByUserIdAndKanjiId(USER_ID, KANJI_ID)).thenReturn(Optional.empty());
        givenSaveReturnsCard();

        srsService.submitReview(USER_ID, reviewRequest(ReviewRating.GOOD, 2_000));

        UserKanjiSrs saved = savedCard();
        assertThat(saved.getStability()).isEqualTo(Fsrs.DEFAULT_PARAMETERS[2]);
        assertThat(saved.getDifficulty()).isCloseTo(2.1175, within(1e-3));
        assertThat(savedLog().getRetrievability()).isNull();
    }

    @Test
    void submitReview_shouldGrowTheFsrsMemory_andLogThePredictedRecall() {
        LocalDateTime now = LocalDateTime.now();
        UserKanjiSrs card = UserKanjiSrs.builder().userId(USER_ID).kanjiId(KANJI_ID).repetitionCount(3)
                .easinessFactor(new BigDecimal("2.40")).reviewIntervalDays(10).nextReviewAt(now)
                .lastReviewedAt(now.minusDays(10)).stability(10.0).difficulty(5.0).build();
        when(kanjiRepository.existsById(KANJI_ID)).thenReturn(true);
        when(srsRepository.findByUserIdAndKanjiId(USER_ID, KANJI_ID)).thenReturn(Optional.of(card));
        givenSaveReturnsCard();

        srsService.submitReview(USER_ID, reviewRequest(ReviewRating.GOOD, 2_000));

        // Độ ổn định 10 ngày, ôn đúng sau 10 ngày: FSRS dự đoán nhớ 90%; nhớ được thì độ ổn định tăng.
        assertThat(savedLog().getRetrievability()).isCloseTo(0.9, within(1e-9));
        assertThat(card.getStability()).isGreaterThan(10.0);
    }

    @Test
    void submitReview_shouldEstimateTheFsrsMemoryFromSm2_forACardReviewedBeforeFsrs() {
        LocalDateTime now = LocalDateTime.now();
        UserKanjiSrs card = UserKanjiSrs.builder().userId(USER_ID).kanjiId(KANJI_ID).repetitionCount(3)
                .easinessFactor(new BigDecimal("2.50")).reviewIntervalDays(15).nextReviewAt(now)
                .lastReviewedAt(now.minusDays(15)).build();
        when(kanjiRepository.existsById(KANJI_ID)).thenReturn(true);
        when(srsRepository.findByUserIdAndKanjiId(USER_ID, KANJI_ID)).thenReturn(Optional.of(card));
        givenSaveReturnsCard();

        srsService.submitReview(USER_ID, reviewRequest(ReviewRating.GOOD, 2_000));

        // Ước lượng: độ ổn định = khoảng ôn SM-2 (15 ngày) nên ôn đúng hạn được dự đoán nhớ 90%.
        assertThat(savedLog().getRetrievability()).isCloseTo(0.9, within(1e-9));
        assertThat(card.getStability()).isGreaterThan(15.0);
        assertThat(card.getDifficulty()).isLessThan(3.0);
    }

    @Test
    void submitReview_shouldScheduleWithFsrs_whenTheLearnerChoseIt() {
        givenScheduling(SchedulerType.FSRS, 0.9);
        when(kanjiRepository.existsById(KANJI_ID)).thenReturn(true);
        when(srsRepository.findByUserIdAndKanjiId(USER_ID, KANJI_ID)).thenReturn(Optional.empty());
        givenSaveReturnsCard();

        srsService.submitReview(USER_ID, reviewRequest(ReviewRating.GOOD, 2_000));

        // "Nhớ" lần đầu cho độ ổn định 2,3 ngày: giữ tỉ lệ nhớ 90% thì 2 ngày sau mới ôn (SM-2 là 1 ngày).
        UserKanjiSrs saved = savedCard();
        assertThat(saved.getReviewIntervalDays()).isEqualTo(2);
        assertThat(ChronoUnit.DAYS.between(saved.getLastReviewedAt(), saved.getNextReviewAt())).isEqualTo(2);
        // SM-2 vẫn chạy song song, đổi lại lúc nào cũng được.
        assertThat(saved.getRepetitionCount()).isEqualTo(1);
        assertThat(saved.getEasinessFactor()).isEqualByComparingTo("2.50");
    }

    @Test
    void submitReview_shouldSpaceReviewsFurther_whenTheLearnerAcceptsForgettingMore() {
        givenScheduling(SchedulerType.FSRS, 0.8);
        when(kanjiRepository.existsById(KANJI_ID)).thenReturn(true);
        when(srsRepository.findByUserIdAndKanjiId(USER_ID, KANJI_ID)).thenReturn(Optional.empty());
        givenSaveReturnsCard();

        srsService.submitReview(USER_ID, reviewRequest(ReviewRating.GOOD, 2_000));

        // Cùng độ ổn định 2,3 ngày, chấp nhận nhớ 80% thì giãn tới 8 ngày.
        assertThat(savedCard().getReviewIntervalDays()).isEqualTo(8);
    }

    @Test
    void submitReview_shouldUseTheLearnersOwnMemoryModel_onceItWasOptimized() {
        // Người này nhớ từ mới lâu hơn người học nói chung: chấm "Nhớ" lần đầu là còn nhớ 90% sau 5 ngày.
        givenScheduling(SchedulerType.FSRS, 0.9, Fsrs.withInitialStabilities(new double[]{0.5, 1.5, 5, 12}));
        when(kanjiRepository.existsById(KANJI_ID)).thenReturn(true);
        when(srsRepository.findByUserIdAndKanjiId(USER_ID, KANJI_ID)).thenReturn(Optional.empty());
        givenSaveReturnsCard();

        srsService.submitReview(USER_ID, reviewRequest(ReviewRating.GOOD, 2_000));

        UserKanjiSrs saved = savedCard();
        assertThat(saved.getStability()).isEqualTo(5);
        assertThat(saved.getReviewIntervalDays()).isEqualTo(5);
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
    void recordQuizAnswer_shouldAddTheWordAsSeenAndDueNow_whenWrongAndNotInReview() {
        when(srsRepository.findByUserIdAndKanjiId(USER_ID, KANJI_ID)).thenReturn(Optional.empty());

        Optional<LocalDateTime> next = srsService.recordQuizAnswer(USER_ID, KANJI_ID, quizAnswer(false, ReviewRating.AGAIN));

        assertThat(next).isPresent();
        verify(srsRepository).insertSeenCardIfAbsent(USER_ID, KANJI_ID, next.get());
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
        verify(srsRepository, never()).insertSeenCardIfAbsent(anyLong(), anyLong(), any());
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
        assertThat(card.getLapseCount()).isEqualTo(1);
        assertThat(savedLog().getScheduled()).isTrue();
    }

    @Test
    void recordQuizAnswer_shouldCountOnlyOneLapse_forMistakesInARowWhileRelearning() {
        UserKanjiSrs card = card(4, "2.60", 20, LocalDateTime.now().plusDays(10));
        when(srsRepository.findByUserIdAndKanjiId(USER_ID, KANJI_ID)).thenReturn(Optional.of(card));
        when(srsRepository.save(any(UserKanjiSrs.class))).thenAnswer(invocation -> invocation.getArgument(0));

        for (int i = 0; i < 6; i++) {
            srsService.recordQuizAnswer(USER_ID, KANJI_ID, quizAnswer(false, ReviewRating.AGAIN));
        }

        assertThat(card.getLapseCount()).isEqualTo(1);
    }

    @Test
    void submitReview_shouldNotCountALapse_whenANewCardIsForgotten() {
        UserKanjiSrs neverReviewed = UserKanjiSrs.builder().userId(USER_ID).kanjiId(KANJI_ID).repetitionCount(0)
                .easinessFactor(new BigDecimal("2.50")).reviewIntervalDays(0).nextReviewAt(LocalDateTime.now()).build();
        when(kanjiRepository.existsById(KANJI_ID)).thenReturn(true);
        when(srsRepository.findByUserIdAndKanjiId(USER_ID, KANJI_ID)).thenReturn(Optional.of(neverReviewed));
        givenSaveReturnsCard();

        srsService.submitReview(USER_ID, reviewRequest(ReviewRating.AGAIN, 1_000));

        assertThat(neverReviewed.getLapseCount()).isZero();
    }

    @Test
    void getDailyCards_shouldFlagHardWords_andMoveThoseBeyondTheFirstFewToTheEnd() {
        srsProperties.setHardWordsUpFront(2);
        List<UserKanjiSrs> due = List.of(dueCard(1L, 6), dueCard(2L, 0), dueCard(3L, 7), dueCard(4L, 9),
                dueCard(5L, 1));
        givenDueCards(due);
        givenPlan(100, 0);
        when(kanjiRepository.findAllById(anyList())).thenReturn(due.stream().map(card -> kanji(card.getKanjiId())).toList());

        List<DailyCardResponse> cards = srsService.getDailyCards(USER_ID, PageRequest.of(0, 20), false).getContent();

        // Cùng mức trễ nên giữ thứ tự; từ khó thứ 3 (id 4) dồn xuống cuối.
        assertThat(cards).extracting(card -> card.getKanji().getId()).containsExactly(1L, 2L, 3L, 5L, 4L);
        assertThat(cards).extracting(DailyCardResponse::isHardWord).containsExactly(true, false, true, false, true);
    }

    @Test
    void getDailyCards_shouldFollowTodaysPlan_andMarkNewWords() {
        UserKanjiSrs newWord = UserKanjiSrs.builder().id(300L).userId(USER_ID).kanjiId(30L).repetitionCount(0)
                .easinessFactor(new BigDecimal("2.50")).reviewIntervalDays(0).nextReviewAt(DUE).lapseCount(0).build();
        List<UserKanjiSrs> due = List.of(dueCard(1L, 0), dueCard(2L, 0), dueCard(3L, 0), newWord);
        givenDueCards(due);
        givenPlan(2, 1);
        when(kanjiRepository.findAllById(anyList())).thenReturn(due.stream().map(card -> kanji(card.getKanjiId())).toList());

        Page<DailyCardResponse> session = srsService.getDailyCards(USER_ID, PageRequest.of(0, 20), false);

        assertThat(session.getTotalElements()).isEqualTo(3);
        assertThat(session.getContent()).extracting(card -> card.getKanji().getId()).containsExactly(1L, 2L, 30L);
        assertThat(session.getContent()).extracting(DailyCardResponse::isNewCard).containsExactly(false, false, true);
    }

    @Test
    void getDailyCards_shouldPreviewTheNextReviewOfEveryRating_withTheLearnersScheduler() {
        UserKanjiSrs newWord = UserKanjiSrs.builder().id(300L).userId(USER_ID).kanjiId(30L).repetitionCount(0)
                .easinessFactor(new BigDecimal("2.50")).reviewIntervalDays(0).nextReviewAt(DUE).lapseCount(0).build();
        List<UserKanjiSrs> due = List.of(dueCard(1L, 0), newWord);
        givenDueCards(due);
        when(kanjiRepository.findAllById(anyList())).thenReturn(due.stream().map(card -> kanji(card.getKanjiId())).toList());

        // SM-2: từ mới chấm gì cũng 1 ngày; từ đã nhớ một lần thì Khó hay Dễ đều 6 ngày.
        assertThat(srsService.getDailyCards(USER_ID, PageRequest.of(0, 20), true).getContent())
                .extracting(card -> card.getKanji().getId(), DailyCardResponse::getIntervals)
                .containsExactlyInAnyOrder(tuple(1L, List.of(1, 6, 6, 6)), tuple(30L, List.of(1, 1, 1, 1)));

        // FSRS: mỗi mức một khoảng ôn - chấm Dễ thì 8 ngày sau mới gặp lại.
        givenScheduling(SchedulerType.FSRS, 0.9);
        assertThat(srsService.getDailyCards(USER_ID, PageRequest.of(0, 20), true).getContent())
                .filteredOn(DailyCardResponse::isNewCard)
                .singleElement()
                .extracting(DailyCardResponse::getIntervals)
                .isEqualTo(List.of(1, 1, 2, 8));
    }

    @Test
    void getDailyCards_shouldPutTheWordsLeastLikelyRemembered_first_withFsrs() {
        LocalDateTime now = LocalDateTime.now();
        // SM-2 coi thẻ 1 trễ hơn (trễ nửa khoảng ôn), nhưng FSRS thấy thẻ 2 yếu hơn nhiều: độ ổn định 3 ngày mà đã
        // 12 ngày chưa ôn (còn nhớ ~78%), còn thẻ 1 ổn định 20 ngày mới qua 3 ngày (~98%).
        UserKanjiSrs strong = UserKanjiSrs.builder().id(101L).userId(USER_ID).kanjiId(1L).repetitionCount(2)
                .easinessFactor(new BigDecimal("2.50")).reviewIntervalDays(2).nextReviewAt(now.minusDays(1))
                .lastReviewedAt(now.minusDays(3)).stability(20.0).difficulty(4.0).lapseCount(0).build();
        UserKanjiSrs weak = UserKanjiSrs.builder().id(102L).userId(USER_ID).kanjiId(2L).repetitionCount(3)
                .easinessFactor(new BigDecimal("2.50")).reviewIntervalDays(10).nextReviewAt(now.minusDays(2))
                .lastReviewedAt(now.minusDays(12)).stability(3.0).difficulty(6.0).lapseCount(0).build();
        List<UserKanjiSrs> due = List.of(strong, weak);
        givenDueCards(due);
        when(kanjiRepository.findAllById(anyList())).thenReturn(due.stream().map(card -> kanji(card.getKanjiId())).toList());

        assertThat(srsService.getDailyCards(USER_ID, PageRequest.of(0, 20), true).getContent())
                .extracting(card -> card.getKanji().getId()).containsExactly(1L, 2L);

        givenScheduling(SchedulerType.FSRS, 0.9);
        assertThat(srsService.getDailyCards(USER_ID, PageRequest.of(0, 20), true).getContent())
                .extracting(card -> card.getKanji().getId()).containsExactly(2L, 1L);
    }

    @Test
    void getDailyCards_shouldTakeEveryDueCard_whenStudyingExtra() {
        List<UserKanjiSrs> due = List.of(dueCard(1L, 0), dueCard(2L, 0), dueCard(3L, 0));
        givenDueCards(due);
        when(kanjiRepository.findAllById(anyList())).thenReturn(due.stream().map(card -> kanji(card.getKanjiId())).toList());

        assertThat(srsService.getDailyCards(USER_ID, PageRequest.of(0, 20), true).getContent()).hasSize(3);
        verify(studyPlanService, never()).today(any());
    }

    @Test
    void saveNote_shouldTrimTheNote_andClearItWhenBlank() {
        UserKanjiSrs card = card(2, "2.50", 6, LocalDateTime.now().plusDays(2));
        when(srsRepository.findByUserIdAndKanjiId(USER_ID, KANJI_ID)).thenReturn(Optional.of(card));

        srsService.saveNote(USER_ID, KANJI_ID, "  KHAI = mở  ");
        assertThat(card.getPersonalNote()).isEqualTo("KHAI = mở");

        srsService.saveNote(USER_ID, KANJI_ID, "   ");
        assertThat(card.getPersonalNote()).isNull();
    }

    @Test
    void saveNote_shouldRefuse_whenTheWordIsNotInTheLearnersReviews() {
        when(srsRepository.findByUserIdAndKanjiId(USER_ID, KANJI_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> srsService.saveNote(USER_ID, KANJI_ID, "ghi chú"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getHardWords_shouldListWordsForgottenAtLeastTheThresholdNumberOfTimes() {
        when(srsRepository.findByUserIdAndLapseCountGreaterThanEqualOrderByLapseCountDesc(USER_ID, 6))
                .thenReturn(List.of(dueCard(8L, 9), dueCard(9L, 6)));
        when(kanjiRepository.findAllById(List.of(8L, 9L))).thenReturn(List.of(kanji(9L), kanji(8L)));

        HardWordsResponse response = srsService.getHardWords(USER_ID);

        assertThat(response.getLapseThreshold()).isEqualTo(6);
        assertThat(response.getWords()).extracting(word -> word.getKanji().getId(), HardWordsResponse.Word::getLapseCount)
                .containsExactly(tuple(8L, 9), tuple(9L, 6));
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

    private static final LocalDateTime DUE = LocalDateTime.now().minusHours(3);

    /** Thẻ đã học, đến hạn cùng lúc với các thẻ khác (cùng mức trễ). */
    private static UserKanjiSrs dueCard(long kanjiId, int lapses) {
        LocalDateTime due = DUE;
        return UserKanjiSrs.builder().id(kanjiId + 100).userId(USER_ID).kanjiId(kanjiId).repetitionCount(1)
                .easinessFactor(new BigDecimal("2.30")).reviewIntervalDays(1).nextReviewAt(due)
                .lastReviewedAt(due.minusDays(1)).lapseCount(lapses).build();
    }

    private static Kanji kanji(long id) {
        return Kanji.builder().id(id).character("語" + id).hanViet("NGỮ").meaning("nghĩa " + id).build();
    }

    private void givenDueCards(List<UserKanjiSrs> due) {
        when(srsRepository.findByUserIdAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc(eq(USER_ID), any(), any()))
                .thenReturn(new PageImpl<>(due));
    }

    private void givenPlan(int reviewsToday, int newToday) {
        when(studyPlanService.today(USER_ID))
                .thenReturn(DailyPlanResponse.builder().reviewsToday(reviewsToday).newToday(newToday).build());
    }

    private void givenScheduling(String scheduler, double desiredRetention) {
        givenScheduling(scheduler, desiredRetention, Fsrs.withDefaults());
    }

    private void givenScheduling(String scheduler, double desiredRetention, Fsrs fsrs) {
        when(learningProfileService.scheduling(USER_ID))
                .thenReturn(new SchedulingSettings(scheduler, desiredRetention, fsrs));
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
