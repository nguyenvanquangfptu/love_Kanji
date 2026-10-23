package com.kanjimastery.backend.service;

import com.kanjimastery.backend.config.SrsProperties;
import com.kanjimastery.backend.dto.GrammarCardResponse;
import com.kanjimastery.backend.dto.GrammarReviewRequest;
import com.kanjimastery.backend.dto.GrammarReviewResponse;
import com.kanjimastery.backend.dto.GrammarSessionResponse;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.exception.ResourceNotFoundException;
import com.kanjimastery.backend.model.CardState;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionStatus;
import com.kanjimastery.backend.model.GrammarPoint;
import com.kanjimastery.backend.model.GrammarReviewLog;
import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.model.ReviewRating;
import com.kanjimastery.backend.model.ReviewState;
import com.kanjimastery.backend.model.SchedulerType;
import com.kanjimastery.backend.model.UserGrammarSrs;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.GrammarPointRepository;
import com.kanjimastery.backend.repository.GrammarReviewLogRepository;
import com.kanjimastery.backend.repository.UserGrammarSrsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrammarSrsServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long POINT_ID = 3L;

    @Mock
    private UserGrammarSrsRepository srsRepository;

    @Mock
    private GrammarReviewLogRepository logRepository;

    @Mock
    private GrammarPointRepository grammarPointRepository;

    @Mock
    private ExamQuestionRepository questionRepository;

    @Spy
    private StudyCalendar calendar = new StudyCalendar(new SrsProperties(),
            Clock.fixed(Instant.parse("2026-10-23T13:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")));

    @Spy
    private CardScheduler cardScheduler = new CardScheduler(new SrsCalculatorService(), calendar);

    @Mock
    private LearningProfileService learningProfileService;

    @Mock
    private FuriganaService furiganaService;

    @Spy
    private SrsProperties srsProperties = new SrsProperties();

    @InjectMocks
    private GrammarSrsService service;

    @BeforeEach
    void scheduleWithSm2ByDefault() {
        lenient().when(learningProfileService.scheduling(anyLong())).thenReturn(SchedulingSettings.DEFAULT);
        lenient().when(srsRepository.save(any(UserGrammarSrs.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void review_shouldGradeTheChosenOptionOnTheServer_aWrongAnswerBeingForgotten() {
        UserGrammarSrs card = givenNewCard();
        givenQuestion(approvedQuestion(10L, "B", POINT_ID));

        GrammarReviewResponse response = service.review(USER_ID, questionAnswer(10L, "c"));

        assertThat(response.correct()).isFalse();
        assertThat(response.correctOption()).isEqualTo("B");
        assertThat(response.explanation()).isEqualTo("giải thích");
        GrammarReviewLog log = savedLog();
        assertThat(log.getRating()).isEqualTo(ReviewRating.AGAIN);
        assertThat(log.getChosenOption()).isEqualTo("C");
        assertThat(log.getQuestionId()).isEqualTo(10L);
        assertThat(log.getStateBefore()).isEqualTo(CardState.NEW);
        assertThat(card.getLastReviewedAt()).isEqualTo(calendar.now());
        assertThat(response.nextReviewAt()).isEqualTo(calendar.startOf(calendar.today().plusDays(1)));
    }

    @Test
    void review_shouldCountARightAnswerAsGood() {
        UserGrammarSrs card = givenNewCard();
        givenQuestion(approvedQuestion(10L, "B", POINT_ID));

        GrammarReviewResponse response = service.review(USER_ID, questionAnswer(10L, "B"));

        assertThat(response.correct()).isTrue();
        assertThat(savedLog().getRating()).isEqualTo(ReviewRating.GOOD);
        assertThat(card.getRepetitionCount()).isEqualTo(1);
    }

    @Test
    void review_shouldRejectAQuestionOfAnotherPointOrNotApproved() {
        givenNewCard();
        givenQuestion(approvedQuestion(10L, "B", 99L));

        assertThatThrownBy(() -> service.review(USER_ID, questionAnswer(10L, "B")))
                .isInstanceOf(BadRequestException.class);

        ExamQuestion draft = approvedQuestion(11L, "B", POINT_ID);
        draft.setReview(new ReviewState(ExamQuestionStatus.DRAFT));
        givenQuestion(draft);
        assertThatThrownBy(() -> service.review(USER_ID, questionAnswer(11L, "B")))
                .isInstanceOf(BadRequestException.class);
        verify(logRepository, never()).save(any());
    }

    @Test
    void review_shouldUseTheLearnersOwnRating_forAFlipCard() {
        givenNewCard();
        GrammarReviewRequest request = new GrammarReviewRequest();
        request.setGrammarPointId(POINT_ID);
        request.setRating(ReviewRating.EASY.value());

        GrammarReviewResponse response = service.review(USER_ID, request);

        assertThat(response.correct()).isTrue();
        assertThat(response.correctOption()).isNull();
        GrammarReviewLog log = savedLog();
        assertThat(log.getRating()).isEqualTo(ReviewRating.EASY);
        assertThat(log.getQuestionId()).isNull();
    }

    @Test
    void review_shouldNeedACardInReview_andARatingForAFlipCard() {
        when(srsRepository.findByUserIdAndGrammarPointId(USER_ID, POINT_ID)).thenReturn(Optional.empty());
        GrammarReviewRequest request = new GrammarReviewRequest();
        request.setGrammarPointId(POINT_ID);
        request.setRating(3);
        assertThatThrownBy(() -> service.review(USER_ID, request)).isInstanceOf(ResourceNotFoundException.class);

        givenNewCard();
        request.setRating(null);
        assertThatThrownBy(() -> service.review(USER_ID, request)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void review_shouldScheduleFsrsWithDefaultParameters_notTheOnesOptimizedFromVocabulary() {
        // Người này nhớ từ vựng lâu hơn hẳn người học nói chung - bộ tham số đó không dùng cho ngữ pháp.
        when(learningProfileService.scheduling(USER_ID)).thenReturn(new SchedulingSettings(SchedulerType.FSRS, 0.9,
                Fsrs.withInitialStabilities(new double[]{0.5, 1.5, 5, 12})));
        UserGrammarSrs card = givenNewCard();
        GrammarReviewRequest request = new GrammarReviewRequest();
        request.setGrammarPointId(POINT_ID);
        request.setRating(ReviewRating.GOOD.value());

        service.review(USER_ID, request);

        // Tham số mặc định: "Nhớ" lần đầu là 2 ngày (bộ tham số riêng sẽ cho 5).
        assertThat(card.getReviewIntervalDays()).isEqualTo(2);
    }

    @Test
    void session_shouldPutDuePointsFirst_thenOnlyTheNewPointsLeftForToday() {
        srsProperties.setGrammarNewPerDay(3);
        UserGrammarSrs due = UserGrammarSrs.builder().userId(USER_ID).grammarPointId(1L)
                .nextReviewAt(calendar.now().minusDays(1)).lastReviewedAt(calendar.now().minusDays(2)).build();
        UserGrammarSrs fresh = UserGrammarSrs.builder().userId(USER_ID).grammarPointId(2L)
                .nextReviewAt(calendar.now()).build();
        when(srsRepository.findByUserIdAndLastReviewedAtIsNotNullAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc(
                eq(USER_ID), any(), any())).thenReturn(List.of(due));
        // Hôm nay đã học 2 điểm mới: còn 1.
        when(logRepository.countNewLearnedSince(USER_ID, calendar.startOfToday()))
                .thenReturn(2L);
        when(srsRepository.findByUserIdAndLastReviewedAtIsNullOrderByIdAsc(USER_ID, PageRequest.of(0, 1)))
                .thenReturn(List.of(fresh));
        when(grammarPointRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(point(1L), point(2L)));
        // Điểm 1 có câu đã duyệt, điểm 2 chưa có: ôn bằng thẻ lật.
        when(questionRepository.pickForGrammarReview(USER_ID, List.of(1L, 2L)))
                .thenReturn(List.of(pick(1L, 10L)));
        when(questionRepository.findAllById(any())).thenReturn(List.of(approvedQuestion(10L, "A", 1L)));

        GrammarSessionResponse session = service.session(USER_ID);

        assertThat(session.newLeftToday()).isEqualTo(1);
        assertThat(session.cards()).extracting(GrammarCardResponse::grammarPointId).containsExactly(1L, 2L);
        assertThat(session.cards()).extracting(GrammarCardResponse::newCard).containsExactly(false, true);
        assertThat(session.cards().get(0).question().id()).isEqualTo(10L);
        assertThat(session.cards().get(1).question()).isNull();
        assertThat(session.cards().get(0).intervals()).hasSize(4);
    }

    @Test
    void session_shouldNotLoadNewPoints_whenTodaysNewPointsAreUsedUp() {
        srsProperties.setGrammarNewPerDay(3);
        when(logRepository.countNewLearnedSince(USER_ID, calendar.startOfToday()))
                .thenReturn(3L);

        GrammarSessionResponse session = service.session(USER_ID);

        assertThat(session.newLeftToday()).isZero();
        assertThat(session.cards()).isEmpty();
        verify(srsRepository, never()).findByUserIdAndLastReviewedAtIsNullOrderByIdAsc(anyLong(), any());
    }

    private UserGrammarSrs givenNewCard() {
        UserGrammarSrs card = UserGrammarSrs.builder().userId(USER_ID).grammarPointId(POINT_ID)
                .nextReviewAt(calendar.now()).build();
        when(srsRepository.findByUserIdAndGrammarPointId(USER_ID, POINT_ID)).thenReturn(Optional.of(card));
        return card;
    }

    private void givenQuestion(ExamQuestion question) {
        when(questionRepository.findAllWithLinksByIdIn(List.of(question.getId()))).thenReturn(List.of(question));
    }

    private static ExamQuestion approvedQuestion(Long id, String correctOption, Long grammarPointId) {
        return ExamQuestion.builder().id(id).questionType(JlptQuestionType.GRAMMAR_FORM)
                .sentence("毎晩（　　）、勉強しています。").optionA("a").optionB("b").optionC("c").optionD("d")
                .correctOption(correctOption).explanation("giải thích")
                .review(new ReviewState(ExamQuestionStatus.APPROVED))
                .grammarPointIds(Set.of(grammarPointId)).build();
    }

    private static GrammarPoint point(Long id) {
        return GrammarPoint.builder().id(id).jlptLevel(JlptLevel.N5).lesson("N5-01").pattern("〜てから")
                .meaningVi("sau khi").build();
    }

    private static ExamQuestionRepository.GrammarReviewQuestion pick(Long pointId, Long questionId) {
        return new ExamQuestionRepository.GrammarReviewQuestion() {
            @Override
            public Long getGrammarPointId() {
                return pointId;
            }

            @Override
            public Long getQuestionId() {
                return questionId;
            }
        };
    }

    private static GrammarReviewRequest questionAnswer(Long questionId, String option) {
        GrammarReviewRequest request = new GrammarReviewRequest();
        request.setGrammarPointId(POINT_ID);
        request.setQuestionId(questionId);
        request.setChosenOption(option);
        request.setResponseMs(4_000);
        return request;
    }

    private GrammarReviewLog savedLog() {
        ArgumentCaptor<GrammarReviewLog> captor = ArgumentCaptor.forClass(GrammarReviewLog.class);
        verify(logRepository).save(captor.capture());
        return captor.getValue();
    }
}
