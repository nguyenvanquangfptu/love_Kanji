package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.ReviewRating;
import com.kanjimastery.backend.repository.ReviewLogRepository;
import com.kanjimastery.backend.repository.ReviewLogRepository.ResponseTimeStats;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResponseTimeRaterTest {

    private static final String DIRECTION = "KANJI_TO_READING";

    @Mock
    private ReviewLogRepository reviewLogRepository;

    @InjectMocks
    private ResponseTimeRater rater;

    @Test
    void normalize_shouldDropMissingNonPositiveAndAwayFromKeyboardTimes() {
        assertThat(ResponseTimeRater.normalize(null)).isNull();
        assertThat(ResponseTimeRater.normalize(0)).isNull();
        assertThat(ResponseTimeRater.normalize(-5)).isNull();
        assertThat(ResponseTimeRater.normalize(60_001)).isNull();
        assertThat(ResponseTimeRater.normalize(60_000)).isEqualTo(60_000);
        assertThat(ResponseTimeRater.normalize(1_200)).isEqualTo(1_200);
    }

    @Test
    void rateCorrectAnswer_shouldBeGoodWithoutQuerying_whenTimeIsUnknown() {
        assertThat(rater.rateCorrectAnswer(7L, DIRECTION, null)).isEqualTo(ReviewRating.GOOD);
        verifyNoInteractions(reviewLogRepository);
    }

    @Test
    void rateCorrectAnswer_shouldUseFixedThresholds_whenLearnerHasTooFewAnswers() {
        givenStats(1_000.0, 29);

        assertThat(rater.rateCorrectAnswer(7L, DIRECTION, 3_000)).isEqualTo(ReviewRating.EASY);
        assertThat(rater.rateCorrectAnswer(7L, DIRECTION, 3_001)).isEqualTo(ReviewRating.GOOD);
        assertThat(rater.rateCorrectAnswer(7L, DIRECTION, 10_000)).isEqualTo(ReviewRating.GOOD);
        assertThat(rater.rateCorrectAnswer(7L, DIRECTION, 10_001)).isEqualTo(ReviewRating.HARD);
    }

    @Test
    void rateCorrectAnswer_shouldUseFixedThresholds_whenLearnerHasNoAnswersYet() {
        givenStats(null, 0);

        assertThat(rater.rateCorrectAnswer(7L, DIRECTION, 2_000)).isEqualTo(ReviewRating.EASY);
        assertThat(rater.rateCorrectAnswer(7L, DIRECTION, 12_000)).isEqualTo(ReviewRating.HARD);
    }

    @Test
    void rateCorrectAnswer_shouldCompareWithLearnersOwnMedian_whenEnoughAnswers() {
        // Người đọc chậm: trung vị 8 giây -> 4,8 giây vẫn là "Dễ", 12 giây vẫn là "Nhớ".
        givenStats(8_000.0, 30);

        assertThat(rater.rateCorrectAnswer(7L, DIRECTION, 4_800)).isEqualTo(ReviewRating.EASY);
        assertThat(rater.rateCorrectAnswer(7L, DIRECTION, 4_801)).isEqualTo(ReviewRating.GOOD);
        assertThat(rater.rateCorrectAnswer(7L, DIRECTION, 12_000)).isEqualTo(ReviewRating.GOOD);
        assertThat(rater.rateCorrectAnswer(7L, DIRECTION, 12_001)).isEqualTo(ReviewRating.HARD);
    }

    private void givenStats(Double medianMs, long samples) {
        when(reviewLogRepository.correctQuizResponseTimes(7L, DIRECTION, ResponseTimeRater.RECENT_SAMPLES))
                .thenReturn(new ResponseTimeStats() {
                    @Override
                    public Double getMedianMs() {
                        return medianMs;
                    }

                    @Override
                    public long getSamples() {
                        return samples;
                    }
                });
    }
}
