package com.kanjimastery.backend.service;

import com.kanjimastery.backend.config.SrsProperties;
import com.kanjimastery.backend.dto.FsrsParametersResponse;
import com.kanjimastery.backend.model.FsrsParameters;
import com.kanjimastery.backend.repository.FsrsParametersRepository;
import com.kanjimastery.backend.repository.ReviewLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Tham số FSRS riêng của từng người: tối ưu từ lịch sử ôn ({@link FsrsOptimizer}), lưu lại và dùng để xếp lịch. */
@Service
@RequiredArgsConstructor
public class FsrsParametersService {

    /** Bộ tham số chỉ dùng được với đúng phiên bản FSRS đã tối ưu ra nó. */
    static final String FSRS_VERSION = "FSRS-6";

    private final FsrsParametersRepository parametersRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final SrsProperties srsProperties;
    private final StudyCalendar calendar;

    /** Mô hình trí nhớ của người học: tham số riêng nếu đã tối ưu, không thì tham số chung. */
    @Transactional(readOnly = true)
    public Fsrs fsrsFor(Long userId) {
        return saved(userId)
                .map(parameters -> new Fsrs(toArray(parameters.getParameters())))
                .orElse(Fsrs.withDefaults());
    }

    @Transactional(readOnly = true)
    public FsrsParametersResponse status(Long userId) {
        return toResponse(saved(userId), FsrsOptimizer.countByRating(firstReviews(userId)));
    }

    /**
     * Tối ưu lại từ toàn bộ lịch sử ôn của người học (job hằng tuần, hoặc khi người học bấm). Chưa mức chấm nào đủ
     * dữ liệu thì giữ nguyên tham số đang dùng.
     */
    @Transactional
    public FsrsParametersResponse optimize(Long userId) {
        List<FsrsOptimizer.FirstReview> reviews = firstReviews(userId);
        int[] counts = FsrsOptimizer.countByRating(reviews);
        Optional<double[]> fitted = FsrsOptimizer.fitInitialStabilities(reviews, srsProperties.getFsrsMinFirstReviews());
        if (fitted.isEmpty()) {
            return toResponse(saved(userId), counts);
        }
        FsrsParameters parameters = parametersRepository.findById(userId)
                .orElseGet(() -> FsrsParameters.builder().userId(userId).build());
        parameters.setFsrsVersion(FSRS_VERSION);
        parameters.setParameters(Arrays.stream(Fsrs.withInitialStabilities(fitted.get()).parameters()).boxed().toList());
        parameters.setFirstReviews(Arrays.stream(counts).sum());
        parameters.setOptimizedAt(calendar.now());
        return toResponse(Optional.of(parametersRepository.save(parameters)), counts);
    }

    /** Tham số đã lưu, nếu tối ưu cho đúng phiên bản FSRS đang dùng. */
    private Optional<FsrsParameters> saved(Long userId) {
        return parametersRepository.findById(userId)
                .filter(parameters -> FSRS_VERSION.equals(parameters.getFsrsVersion()));
    }

    /** Lần học đầu của mỗi từ và lần ôn kế tiếp, khoảng cách tính theo ngày học. */
    private List<FsrsOptimizer.FirstReview> firstReviews(Long userId) {
        return reviewLogRepository.firstReviewOutcomes(userId).stream()
                .map(row -> new FsrsOptimizer.FirstReview(row.getRating(),
                        ChronoUnit.DAYS.between(calendar.dayOf(row.getFirstAt()), calendar.dayOf(row.getNextAt())),
                        row.getRecalled()))
                .toList();
    }

    private FsrsParametersResponse toResponse(Optional<FsrsParameters> saved, int[] counts) {
        double[] current = saved.map(parameters -> toArray(parameters.getParameters())).orElse(Fsrs.DEFAULT_PARAMETERS);
        return FsrsParametersResponse.builder()
                .personalized(saved.isPresent())
                .optimizedAt(saved.map(FsrsParameters::getOptimizedAt).orElse(null))
                .firstReviews(Arrays.stream(counts).boxed().toList())
                .minFirstReviews(srsProperties.getFsrsMinFirstReviews())
                .initialStabilities(initialStabilities(current))
                .defaultInitialStabilities(initialStabilities(Fsrs.DEFAULT_PARAMETERS))
                .build();
    }

    private static List<Double> initialStabilities(double[] parameters) {
        return Arrays.stream(parameters, 0, 4).boxed().toList();
    }

    private static double[] toArray(List<Double> parameters) {
        return parameters.stream().mapToDouble(Double::doubleValue).toArray();
    }
}
