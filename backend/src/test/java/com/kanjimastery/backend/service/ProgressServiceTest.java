package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.QuizDirection;
import com.kanjimastery.backend.config.SrsProperties;
import com.kanjimastery.backend.dto.ProgressResponse;
import com.kanjimastery.backend.model.CardState;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.repository.KanjiRepository;
import com.kanjimastery.backend.repository.ReviewLogRepository;
import com.kanjimastery.backend.repository.ReviewLogRepository.Activity;
import com.kanjimastery.backend.repository.ReviewLogRepository.Calibration;
import com.kanjimastery.backend.repository.ReviewLogRepository.DirectionStats;
import com.kanjimastery.backend.repository.ReviewLogRepository.QuizMistake;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgressServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private ReviewLogRepository reviewLogRepository;
    @Mock
    private KanjiRepository kanjiRepository;

    private ProgressService service;

    @BeforeEach
    void setUp() {
        // Máy chủ chạy UTC; 10:00 UTC thứ Sáu 02/10/2026 = 17:00 ở Việt Nam.
        StudyCalendar calendar = new StudyCalendar(new SrsProperties(),
                Clock.fixed(Instant.parse("2026-10-02T10:00:00Z"), ZoneOffset.UTC));
        service = new ProgressService(reviewLogRepository, kanjiRepository, calendar);
    }

    @Test
    void get_shouldBucketAnswersByVietnameseStudyDayAndWeek() {
        // 8 tuần từ thứ Hai 10/08, ngày học bắt đầu 4:00 giờ Việt Nam = 21:00 UTC hôm trước.
        when(reviewLogRepository.activitySince(USER_ID, LocalDateTime.of(2026, 8, 9, 21, 0))).thenReturn(List.of(
                activity("2026-10-02T02:00", CardState.REVIEW, true, true),      // 09:00 ngày 02/10, nhớ
                activity("2026-10-02T03:00", CardState.NEW, false, true),        // luyện trắc nghiệm, không vào lịch
                activity("2026-10-02T04:00", CardState.RELEARNING, true, true),  // học lại: không tính tỉ lệ nhớ
                activity("2026-10-01T20:00", CardState.NEW, true, true),         // 03:00 sáng 02/10 = ngày học 01/10
                activity("2026-09-21T03:00", CardState.REVIEW, true, false)));   // tuần 21/09, quên
        when(reviewLogRepository.quizDirectionStats(eq(USER_ID), any())).thenReturn(List.of());
        when(reviewLogRepository.topQuizMistakes(eq(USER_ID), any(), anyInt())).thenReturn(List.of());
        givenCalibration(0, null, null);

        ProgressResponse progress = service.get(USER_ID);

        assertThat(progress.getDays()).hasSize(14);
        assertThat(progress.getDays().get(0).getDay()).isEqualTo(LocalDate.of(2026, 9, 19));
        assertThat(progress.getDays()).filteredOn(day -> day.getDay().equals(LocalDate.of(2026, 10, 2)))
                .singleElement().satisfies(day -> {
                    assertThat(day.getReviews()).isEqualTo(3);
                    assertThat(day.getNewWords()).isZero();
                });
        assertThat(progress.getDays()).filteredOn(day -> day.getDay().equals(LocalDate.of(2026, 10, 1)))
                .singleElement().satisfies(day -> assertThat(day.getNewWords()).isEqualTo(1));

        assertThat(progress.getWeeks()).hasSize(8);
        assertThat(progress.getWeeks().get(0).getWeekStart()).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(progress.getWeeks()).extracting(ProgressResponse.Week::getWeekStart,
                        ProgressResponse.Week::getReviews, ProgressResponse.Week::getRemembered)
                .contains(tuple(LocalDate.of(2026, 9, 28), 1L, 1L), tuple(LocalDate.of(2026, 9, 21), 1L, 0L));
    }

    @Test
    void get_shouldReportQuizAccuracyPerDirection_andTheMostCommonMistakes() {
        when(reviewLogRepository.activitySince(eq(USER_ID), any())).thenReturn(List.of());
        when(reviewLogRepository.quizDirectionStats(eq(USER_ID), any()))
                .thenReturn(List.of(directionStats(QuizDirection.KANJI_TO_READING, 20, 5), directionStats(null, 3, 1)));
        when(reviewLogRepository.topQuizMistakes(eq(USER_ID), any(), anyInt()))
                .thenReturn(List.of(mistake(60L, "持つ", 3), mistake(99L, "消えた", 2)));
        // Từ 99 đã bị xoá khỏi kho: bỏ qua.
        when(kanjiRepository.findAllById(anyList())).thenReturn(List.of(
                Kanji.builder().id(60L).character("待つ").reading("まつ").meaning("Đợi").build()));
        givenCalibration(0, null, null);

        ProgressResponse progress = service.get(USER_ID);

        assertThat(progress.getDirections()).singleElement().satisfies(direction -> {
            assertThat(direction.getDirection()).isEqualTo(QuizDirection.KANJI_TO_READING);
            assertThat(direction.getAnswers()).isEqualTo(20);
            assertThat(direction.getCorrect()).isEqualTo(15);
        });
        assertThat(progress.getConfusions()).singleElement().satisfies(confusion -> {
            assertThat(confusion.getCharacter()).isEqualTo("待つ");
            assertThat(confusion.getChosenAnswer()).isEqualTo("持つ");
            assertThat(confusion.getTimes()).isEqualTo(3);
        });
    }

    @Test
    void get_shouldCompareFsrsPredictionsWithWhatWasRemembered_onceThereAreEnoughReviews() {
        when(reviewLogRepository.activitySince(eq(USER_ID), any())).thenReturn(List.of());
        when(reviewLogRepository.quizDirectionStats(eq(USER_ID), any())).thenReturn(List.of());
        when(reviewLogRepository.topQuizMistakes(eq(USER_ID), any(), anyInt())).thenReturn(List.of());

        givenCalibration(19, 0.9, 0.7);
        assertThat(service.get(USER_ID).getCalibration()).isNull();

        // 30 ngày tính từ 17:00 ngày 02/10 giờ Việt Nam.
        when(reviewLogRepository.calibration(USER_ID, LocalDateTime.of(2026, 9, 2, 10, 0)))
                .thenReturn(calibration(40, 0.88, 0.8));
        assertThat(service.get(USER_ID).getCalibration()).satisfies(calibration -> {
            assertThat(calibration.getReviews()).isEqualTo(40);
            assertThat(calibration.getPredicted()).isEqualTo(0.88);
            assertThat(calibration.getActual()).isEqualTo(0.8);
        });
    }

    private void givenCalibration(long reviews, Double predicted, Double actual) {
        when(reviewLogRepository.calibration(eq(USER_ID), any())).thenReturn(calibration(reviews, predicted, actual));
    }

    private static Calibration calibration(long reviews, Double predicted, Double actual) {
        return new Calibration() {
            @Override
            public long getReviews() {
                return reviews;
            }

            @Override
            public Double getPredicted() {
                return predicted;
            }

            @Override
            public Double getActual() {
                return actual;
            }
        };
    }

    private static Activity activity(String utc, CardState stateBefore, boolean scheduled, boolean correct) {
        return new Activity() {
            @Override
            public LocalDateTime getReviewedAt() {
                return LocalDateTime.parse(utc);
            }

            @Override
            public CardState getStateBefore() {
                return stateBefore;
            }

            @Override
            public Boolean getScheduled() {
                return scheduled;
            }

            @Override
            public Boolean getCorrect() {
                return correct;
            }
        };
    }

    private static DirectionStats directionStats(QuizDirection direction, long answers, long errors) {
        return new DirectionStats() {
            @Override
            public QuizDirection getDirection() {
                return direction;
            }

            @Override
            public long getAnswers() {
                return answers;
            }

            @Override
            public long getErrors() {
                return errors;
            }
        };
    }

    private static QuizMistake mistake(Long kanjiId, String chosenAnswer, long times) {
        return new QuizMistake() {
            @Override
            public Long getKanjiId() {
                return kanjiId;
            }

            @Override
            public QuizDirection getDirection() {
                return QuizDirection.READING_TO_KANJI;
            }

            @Override
            public String getChosenAnswer() {
                return chosenAnswer;
            }

            @Override
            public long getTimes() {
                return times;
            }
        };
    }
}
