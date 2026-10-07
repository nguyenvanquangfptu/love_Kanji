package com.kanjimastery.backend.service;

import com.kanjimastery.backend.config.SrsProperties;
import com.kanjimastery.backend.dto.FsrsParametersResponse;
import com.kanjimastery.backend.model.FsrsParameters;
import com.kanjimastery.backend.model.ReviewRating;
import com.kanjimastery.backend.repository.FsrsParametersRepository;
import com.kanjimastery.backend.repository.ReviewLogRepository;
import com.kanjimastery.backend.repository.ReviewLogRepository.FirstReviewOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FsrsParametersServiceTest {

    private static final Long USER_ID = 7L;
    private static final LocalDateTime LEARNED = LocalDateTime.of(2026, 9, 1, 20, 0);

    @Mock
    private FsrsParametersRepository parametersRepository;

    @Mock
    private ReviewLogRepository reviewLogRepository;

    private FsrsParametersService service;

    @BeforeEach
    void setUp() {
        SrsProperties properties = new SrsProperties();
        service = new FsrsParametersService(parametersRepository, reviewLogRepository, properties,
                new StudyCalendar(properties, Clock.systemDefaultZone()));
    }

    @Test
    void optimize_shouldSaveThePersonalParameters_onceARatingHasEnoughFirstReviews() {
        // 60 từ chấm "Nhớ" lần đầu, hôm sau chỉ còn nhớ 36 từ (60%).
        givenFirstReviews(ReviewRating.GOOD, 1, 60, 36);
        when(parametersRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(parametersRepository.save(any(FsrsParameters.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FsrsParametersResponse status = service.optimize(USER_ID);

        ArgumentCaptor<FsrsParameters> captor = ArgumentCaptor.forClass(FsrsParameters.class);
        verify(parametersRepository).save(captor.capture());
        FsrsParameters saved = captor.getValue();
        assertThat(saved.getFsrsVersion()).isEqualTo("FSRS-6");
        assertThat(saved.getFirstReviews()).isEqualTo(60);
        assertThat(saved.getParameters()).hasSize(21);
        // Chỉ độ ổn định ban đầu (w0-w3) thay đổi, phần còn lại giữ tham số chung.
        assertThat(saved.getParameters().subList(4, 21))
                .containsExactlyElementsOf(Arrays.stream(Fsrs.DEFAULT_PARAMETERS, 4, 21).boxed().toList());
        assertThat(saved.getParameters().get(ReviewRating.GOOD - 1))
                .isLessThan(Fsrs.DEFAULT_PARAMETERS[ReviewRating.GOOD - 1]);

        assertThat(status.isPersonalized()).isTrue();
        assertThat(status.getFirstReviews()).containsExactly(0, 0, 60, 0);
        assertThat(status.getInitialStabilities()).isEqualTo(saved.getParameters().subList(0, 4));
        assertThat(status.getDefaultInitialStabilities().get(ReviewRating.GOOD - 1))
                .isEqualTo(Fsrs.DEFAULT_PARAMETERS[ReviewRating.GOOD - 1]);
    }

    @Test
    void optimize_shouldKeepTheSharedParameters_whileThereIsTooLittleData() {
        givenFirstReviews(ReviewRating.GOOD, 2, 12, 11);
        when(parametersRepository.findById(USER_ID)).thenReturn(Optional.empty());

        FsrsParametersResponse status = service.optimize(USER_ID);

        verify(parametersRepository, never()).save(any());
        assertThat(status.isPersonalized()).isFalse();
        assertThat(status.getFirstReviews()).containsExactly(0, 0, 12, 0);
        assertThat(status.getMinFirstReviews()).isEqualTo(50);
        assertThat(status.getInitialStabilities()).isEqualTo(status.getDefaultInitialStabilities());
    }

    @Test
    void fsrsFor_shouldUseTheSavedParameters_onlyIfTheyWereFittedForThisFsrsVersion() {
        List<Double> personal = Arrays.stream(Fsrs.withInitialStabilities(new double[]{0.5, 1.5, 5, 12}).parameters())
                .boxed().toList();
        when(parametersRepository.findById(USER_ID)).thenReturn(Optional.of(saved("FSRS-6", personal)));
        assertThat(service.fsrsFor(USER_ID).first(ReviewRating.GOOD).stability()).isEqualTo(5);

        when(parametersRepository.findById(USER_ID)).thenReturn(Optional.of(saved("FSRS-5", personal)));
        assertThat(service.fsrsFor(USER_ID)).isSameAs(Fsrs.withDefaults());
    }

    private void givenFirstReviews(int rating, int days, int count, int recalled) {
        List<FirstReviewOutcome> rows = IntStream.range(0, count)
                .mapToObj(i -> outcome(rating, LEARNED, LEARNED.plusDays(days), i < recalled))
                .toList();
        when(reviewLogRepository.firstReviewOutcomes(USER_ID)).thenReturn(rows);
    }

    private static FsrsParameters saved(String version, List<Double> parameters) {
        return FsrsParameters.builder().userId(USER_ID).fsrsVersion(version).parameters(parameters).firstReviews(80)
                .optimizedAt(LEARNED).build();
    }

    private static FirstReviewOutcome outcome(int rating, LocalDateTime firstAt, LocalDateTime nextAt, boolean recalled) {
        return new FirstReviewOutcome() {
            @Override
            public Short getRating() {
                return (short) rating;
            }

            @Override
            public LocalDateTime getFirstAt() {
                return firstAt;
            }

            @Override
            public LocalDateTime getNextAt() {
                return nextAt;
            }

            @Override
            public Boolean getRecalled() {
                return recalled;
            }
        };
    }
}
