package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.config.SrsProperties;
import com.kanjimastery.backend.dto.DailyCardResponse;
import com.kanjimastery.backend.dto.FsrsParametersResponse;
import com.kanjimastery.backend.dto.LearningProfileRequest;
import com.kanjimastery.backend.dto.ReviewRequest;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.model.ReviewRating;
import com.kanjimastery.backend.model.SchedulerType;
import com.kanjimastery.backend.model.User;
import com.kanjimastery.backend.repository.KanjiRepository;
import com.kanjimastery.backend.repository.ReviewLogRepository;
import com.kanjimastery.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mô phỏng hai người học giống hệt nhau trong {@value #DAYS} ngày bằng chính code của app - chỉ có đồng hồ được tua
 * qua từng ngày. Trí nhớ "thật" của người học theo FSRS-6 nhưng độ ổn định sau lần học đầu chỉ bằng khoảng 0,4 lần
 * mặc định (người này quên từ mới nhanh hơn người học nói chung): lần ôn nào nhớ hay quên được bốc theo xác suất nhớ
 * thật. Mỗi ngày học {@value #NEW_PER_DAY} từ mới và ôn mọi thẻ đến hạn vào một giờ ngẫu nhiên buổi tối (có hôm quá
 * nửa đêm), nghỉ khoảng 10% số ngày. Một người xếp lịch bằng SM-2, một người bằng FSRS (giữ 90%) và được tối ưu tham số
 * mỗi tuần như job thật.
 */
class FsrsSimulationIT extends AbstractIntegrationTest {

    static final int DAYS = 120;
    static final int NEW_PER_DAY = 8;
    static final int WORDS = 640;
    static final double SKIP_DAY = 0.1;
    static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    /** Độ ổn định ban đầu thật của người học, khoảng 0,4 lần tham số chung (0,212 / 1,29 / 2,31 / 8,30 ngày). */
    static final double[] TRUE_INITIAL_STABILITIES = {0.08, 0.5, 0.9, 3.4};

    /** Đồng hồ tua được, cho mọi service đọc giờ qua {@link StudyCalendar}. */
    static final class SimulatedClock extends Clock {
        private Instant instant = Instant.now();

        void set(Instant instant) {
            this.instant = instant;
        }

        void plusSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.systemDefault();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @TestConfiguration
    static class SimulatedTime {
        @Bean
        SimulatedClock simulatedClock() {
            return new SimulatedClock();
        }

        @Bean
        @Primary
        StudyCalendar simulatedCalendar(SrsProperties properties, SimulatedClock clock) {
            return new StudyCalendar(properties, clock);
        }
    }

    /** Một người học ảo: trí nhớ thật của từng từ và các số đo trong lúc học. */
    static final class Learner {
        final String name;
        final Long userId;
        final Random random;
        final Map<Long, Fsrs.Memory> memory = new HashMap<>();
        final Map<Long, LocalDate> lastStudied = new HashMap<>();
        /** Theo ngày mô phỏng: số lượt ôn (không tính học từ mới) và số lượt nhớ được. */
        final int[] reviews = new int[DAYS];
        final int[] remembered = new int[DAYS];
        final Set<LocalDate> studyDays = new HashSet<>();
        /** Thẻ hiện ra muộn hơn ngày đến hạn dù hôm đến hạn người học có học. */
        int lateDespiteStudying;

        Learner(String name, Long userId, long seed) {
            this.name = name;
            this.userId = userId;
            this.random = new Random(seed);
        }

        int reviews(int fromDay, int toDay) {
            int sum = 0;
            for (int day = fromDay; day < toDay; day++) {
                sum += reviews[day];
            }
            return sum;
        }

        double retention(int fromDay, int toDay) {
            int sum = 0;
            for (int day = fromDay; day < toDay; day++) {
                sum += remembered[day];
            }
            return sum / (double) Math.max(reviews(fromDay, toDay), 1);
        }
    }

    @Autowired
    private SimulatedClock clock;
    @Autowired
    private StudyCalendar calendar;
    @Autowired
    private SrsService srsService;
    @Autowired
    private LearningProfileService learningProfileService;
    @Autowired
    private FsrsParametersService fsrsParametersService;
    @Autowired
    private ReviewLogRepository reviewLogRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private KanjiRepository kanjiRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private final Fsrs truth = Fsrs.withInitialStabilities(TRUE_INITIAL_STABILITIES);
    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> wordIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        userIds.forEach(userRepository::deleteById);
        kanjiRepository.deleteAllById(wordIds);
    }

    @Test
    void learnersOverFourMonths_shouldKeepFsrsHonestAndRecoverTheirOwnStartingMemory() throws IOException {
        // Kết thúc vào hôm qua để lịch sử xuất ra (target/fsrs-simulation) nạp thẳng được vào app đang chạy.
        LocalDate firstDay = LocalDate.now(VIETNAM).minusDays(DAYS);
        for (int i = 0; i < WORDS; i++) {
            wordIds.add(kanjiRepository.save(Kanji.builder().character(String.format("模%04d", i))
                    .reading("も" + i).hanViet("MÔ").meaning("Từ mô phỏng " + i).jlptLevel(JlptLevel.N4).strokeCount(14)
                    .build()).getId());
        }
        Learner sm2 = learner("sm2", SchedulerType.SM2);
        Learner fsrs = learner("fsrs", SchedulerType.FSRS);

        List<String> optimizations = new ArrayList<>();
        Random calendarRandom = new Random(Long.getLong("simulation.seed", 42) + 7);
        for (int day = 0; day < DAYS; day++) {
            boolean studies = day == 0 || calendarRandom.nextDouble() >= SKIP_DAY;
            // Học từ 19:00 tới 01:00 hôm sau - quá nửa đêm vẫn là ngày học hôm trước (ngày học bắt đầu lúc 4 giờ).
            LocalDateTime start = firstDay.plusDays(day).atTime(19, 0).plusMinutes(calendarRandom.nextInt(360));
            if (!studies) {
                continue;
            }
            List<Long> newWords = wordIds.subList(Math.min(day * NEW_PER_DAY, WORDS),
                    Math.min((day + 1) * NEW_PER_DAY, WORDS));
            for (Learner learner : List.of(sm2, fsrs)) {
                clock.set(start.atZone(VIETNAM).toInstant());
                studyOneDay(learner, newWords, day);
            }
            if (day % 7 == 6) {
                FsrsParametersResponse status = fsrsParametersService.optimize(fsrs.userId);
                if (status.isPersonalized()) {
                    optimizations.add("ngày " + day + ": " + status.getInitialStabilities().stream()
                            .map(s -> String.format("%.2f", s)).collect(Collectors.joining(" / ")));
                }
            }
        }

        FsrsParametersResponse status = fsrsParametersService.status(fsrs.userId);
        ReviewLogRepository.Calibration sm2Calibration = reviewLogRepository.calibration(sm2.userId,
                calendar.now().minusDays(30));
        ReviewLogRepository.Calibration fsrsCalibration = reviewLogRepository.calibration(fsrs.userId,
                calendar.now().minusDays(30));
        ReviewLogRepository.Calibration fsrsEarlyCalibration = calibrationBetween(fsrs.userId, firstDay, 0, 40);

        String report = String.join("\n",
                "Mô phỏng " + DAYS + " ngày, " + WORDS + " từ, độ ổn định ban đầu thật "
                        + format(TRUE_INITIAL_STABILITIES) + " (mặc định " + format(Fsrs.DEFAULT_PARAMETERS) + ")",
                "SM-2 : " + sm2.reviews(0, DAYS) + " lượt ôn, nhớ " + percent(sm2.retention(0, DAYS))
                        + ", 30 ngày cuối " + sm2.reviews(DAYS - 30, DAYS) + " lượt, nhớ "
                        + percent(sm2.retention(DAYS - 30, DAYS)),
                "FSRS : " + fsrs.reviews(0, DAYS) + " lượt ôn, nhớ " + percent(fsrs.retention(0, DAYS))
                        + ", 30 ngày cuối " + fsrs.reviews(DAYS - 30, DAYS) + " lượt, nhớ "
                        + percent(fsrs.retention(DAYS - 30, DAYS)),
                "Tối ưu FSRS mỗi tuần (Quên / Khó / Nhớ / Dễ): " + String.join("; ", optimizations),
                "Thẻ hiện muộn dù hôm đến hạn có học: SM-2 " + sm2.lateDespiteStudying + ", FSRS " + fsrs.lateDespiteStudying,
                "Từ đã học và ôn lại theo mức chấm đầu: " + status.getFirstReviews(),
                "Lần ôn kế tiếp sau lần học đầu, nhớ thật so với trí nhớ thật kỳ vọng: " + firstReviewCheck(fsrs.userId),
                "Dự đoán so với thực tế, FSRS 40 ngày đầu: " + calibration(fsrsEarlyCalibration),
                "Dự đoán so với thực tế, FSRS 30 ngày cuối: " + calibration(fsrsCalibration),
                "Dự đoán so với thực tế, SM-2 30 ngày cuối (FSRS chạy song song): " + calibration(sm2Calibration));
        System.out.println("\n===== MÔ PHỎNG FSRS =====\n" + report + "\n=========================");
        export(fsrs, status, report);

        // Đủ 50 từ cho mỗi mức chấm ở lần học đầu -> có tham số riêng, và tìm ra người này quên từ mới nhanh hơn. Mức
        // "Nhớ" có nhiều dữ liệu nhất nên ước lượng sát (thật 0,9 ngày, chung 2,3); các mức ít dữ liệu dao động hơn,
        // "Dễ" bị kéo về mặc định khi dữ liệu chưa đủ rõ - nên chỉ đòi tính chung là thấp hơn mặc định.
        assertThat(status.isPersonalized()).isTrue();
        assertThat(status.getFirstReviews()).allSatisfy(count -> assertThat(count).isGreaterThanOrEqualTo(50));
        List<Double> fitted = status.getInitialStabilities();
        assertThat(fitted.get(ReviewRating.GOOD.value() - 1)).isBetween(0.45, 1.6);
        double logRatio = 0;
        for (int rating = 0; rating < 4; rating++) {
            logRatio += Math.log(fitted.get(rating) / Fsrs.DEFAULT_PARAMETERS[rating]);
        }
        assertThat(Math.exp(logRatio / 4)).as("trung bình nhân của tham số riêng / tham số chung").isLessThan(0.8);

        // Thẻ đến hạn từ đầu ngày học: hôm đến hạn có học thì không thẻ nào bị dời sang hôm sau.
        assertThat(sm2.lateDespiteStudying).isZero();
        assertThat(fsrs.lateDespiteStudying).isZero();
        // Xếp lịch bằng FSRS giữ tỉ lệ nhớ thật quanh mức người học chọn (90%).
        assertThat(fsrs.retention(DAYS - 30, DAYS)).isBetween(0.86, 0.94);
        // Sau khi tối ưu, xác suất nhớ FSRS dự đoán khớp với thực tế.
        assertThat(Math.abs(fsrsCalibration.getPredicted() - fsrsCalibration.getActual())).isLessThan(0.04);
        // Mọi khoảng ôn hợp lệ, lần ôn tới luôn sau lần ôn vừa rồi.
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_kanji_srs
                WHERE user_id IN (?, ?) AND (review_interval_days < 1 OR review_interval_days > 36500
                      OR next_review_at <= last_reviewed_at OR stability <= 0 OR difficulty NOT BETWEEN 1 AND 10)
                """, Long.class, sm2.userId, fsrs.userId)).isZero();
    }

    private Learner learner(String name, SchedulerType scheduler) {
        String suffix = name + "_" + System.nanoTime();
        Long userId = userRepository.save(User.builder().username("sim_" + suffix).email("sim_" + suffix + "@test.local")
                .passwordHash("x").build()).getId();
        userIds.add(userId);
        LearningProfileRequest profile = new LearningProfileRequest();
        profile.setDailyMinutes(30);
        profile.setScheduler(scheduler);
        profile.setDesiredRetention(new BigDecimal("0.90"));
        learningProfileService.update(userId, profile);
        // Cùng hạt giống: hai người học giống hệt nhau, chỉ khác cách xếp lịch.
        return new Learner(name, userId, Long.getLong("simulation.seed", 42));
    }

    /** Thêm từ mới của hôm nay rồi làm hết phiên ôn: thẻ đến hạn và từ mới, theo đúng thứ tự app xếp. */
    private void studyOneDay(Learner learner, List<Long> newWords, int day) {
        if (!newWords.isEmpty()) {
            srsService.addCards(learner.userId, newWords);
        }
        List<DailyCardResponse> session = srsService.getDailyCards(learner.userId, PageRequest.of(0, 2000), true)
                .getContent();
        LocalDate today = calendar.today();
        learner.studyDays.add(today);
        for (DailyCardResponse card : session) {
            LocalDate dueDay = calendar.dayOf(card.getNextReviewAt());
            if (card.getLastReviewedAt() != null && dueDay.isBefore(today) && learner.studyDays.contains(dueDay)) {
                learner.lateDespiteStudying++;
            }
            Long kanjiId = card.getKanji().getId();
            int rating;
            Fsrs.Memory memory = learner.memory.get(kanjiId);
            if (memory == null) {
                rating = firstRating(learner.random);
                learner.memory.put(kanjiId, truth.first(rating));
            } else {
                long elapsed = ChronoUnit.DAYS.between(learner.lastStudied.get(kanjiId), today);
                boolean recalled = learner.random.nextDouble() < truth.retrievability(memory.stability(), elapsed);
                rating = recalled ? recalledRating(learner.random) : ReviewRating.AGAIN.value();
                learner.memory.put(kanjiId, truth.next(memory, rating, elapsed));
                learner.reviews[day]++;
                if (recalled) {
                    learner.remembered[day]++;
                }
            }
            learner.lastStudied.put(kanjiId, today);
            ReviewRequest request = new ReviewRequest();
            request.setKanjiId(kanjiId);
            request.setRating(rating);
            request.setResponseMs(3_000);
            srsService.submitReview(learner.userId, request);
            clock.plusSeconds(8);
        }
    }

    /** Theo mức chấm đầu: số từ, tỉ lệ còn nhớ ở lần ôn kế tiếp, và tỉ lệ trí nhớ thật cho phép (kỳ vọng). */
    private String firstReviewCheck(Long userId) {
        int[] count = new int[4];
        int[] recalled = new int[4];
        double[] expected = new double[4];
        for (ReviewLogRepository.FirstReviewOutcome row : reviewLogRepository.firstReviewOutcomes(userId)) {
            long elapsed = ChronoUnit.DAYS.between(calendar.dayOf(row.getFirstAt()), calendar.dayOf(row.getNextAt()));
            int r = row.getRating() - 1;
            count[r]++;
            recalled[r] += Boolean.TRUE.equals(row.getRecalled()) ? 1 : 0;
            expected[r] += truth.retrievability(TRUE_INITIAL_STABILITIES[r], elapsed);
        }
        List<String> parts = new ArrayList<>();
        for (int r = 0; r < 4; r++) {
            parts.add(String.format("mức %d: %d từ, %s / %s", r + 1, count[r], percent(recalled[r] / (double) count[r]),
                    percent(expected[r] / count[r])));
        }
        return String.join("; ", parts);
    }

    /** Lần học đầu: 15% Quên, 15% Khó, 55% Nhớ, 15% Dễ. */
    private static int firstRating(Random random) {
        double draw = random.nextDouble();
        return draw < 0.15 ? ReviewRating.AGAIN.value() : draw < 0.30 ? ReviewRating.HARD.value()
                : draw < 0.85 ? ReviewRating.GOOD.value() : ReviewRating.EASY.value();
    }

    /** Nhớ được: 10% Khó, 80% Nhớ, 10% Dễ. */
    private static int recalledRating(Random random) {
        double draw = random.nextDouble();
        return draw < 0.10 ? ReviewRating.HARD.value() : draw < 0.90 ? ReviewRating.GOOD.value() : ReviewRating.EASY.value();
    }

    private ReviewLogRepository.Calibration calibrationBetween(Long userId, LocalDate firstDay, int fromDay, int toDay) {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT COUNT(*) AS reviews, AVG(retrievability) AS predicted,
                       AVG(CASE WHEN correct THEN 1.0 ELSE 0.0 END) AS actual
                FROM review_logs
                WHERE user_id = ? AND scheduled AND retrievability < 1 AND reviewed_at >= ? AND reviewed_at < ?
                """, userId, calendar.startOf(firstDay.plusDays(fromDay)), calendar.startOf(firstDay.plusDays(toDay)));
        long reviews = ((Number) row.get("reviews")).longValue();
        Double predicted = row.get("predicted") == null ? null : ((Number) row.get("predicted")).doubleValue();
        Double actual = row.get("actual") == null ? null : ((Number) row.get("actual")).doubleValue();
        return new ReviewLogRepository.Calibration() {
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

    /** Lịch sử của người học FSRS (từ đánh số theo thứ tự học) để nạp vào app chạy thật xem giao diện. */
    private void export(Learner learner, FsrsParametersResponse status, String report) throws IOException {
        Map<Long, Integer> index = new HashMap<>();
        for (int i = 0; i < wordIds.size(); i++) {
            index.put(wordIds.get(i), i);
        }
        Path dir = Path.of("target", "fsrs-simulation");
        Files.createDirectories(dir);
        List<String> logs = jdbc.query("""
                SELECT kanji_id, source, correct, rating, state_before, ef_before, interval_before, scheduled,
                       retrievability, reviewed_at
                FROM review_logs WHERE user_id = ? ORDER BY id
                """, (rs, n) -> String.join("\t", String.valueOf(index.get(rs.getLong("kanji_id"))),
                rs.getString("source"), rs.getString("correct"), rs.getString("rating"), rs.getString("state_before"),
                String.valueOf(rs.getString("ef_before")), String.valueOf(rs.getString("interval_before")),
                rs.getString("scheduled"), String.valueOf(rs.getString("retrievability")),
                rs.getString("reviewed_at")), learner.userId);
        List<String> cards = jdbc.query("""
                SELECT kanji_id, repetition_count, easiness_factor, review_interval_days, next_review_at,
                       last_reviewed_at, lapse_count, stability, difficulty
                FROM user_kanji_srs WHERE user_id = ? ORDER BY id
                """, (rs, n) -> String.join("\t", String.valueOf(index.get(rs.getLong("kanji_id"))),
                rs.getString("repetition_count"), rs.getString("easiness_factor"),
                rs.getString("review_interval_days"), rs.getString("next_review_at"),
                String.valueOf(rs.getString("last_reviewed_at")), rs.getString("lapse_count"),
                String.valueOf(rs.getString("stability")), String.valueOf(rs.getString("difficulty"))), learner.userId);
        String parameters = jdbc.queryForObject(
                "SELECT parameters::text || E'\\t' || first_reviews || E'\\t' || optimized_at FROM user_fsrs_parameters WHERE user_id = ?",
                String.class, learner.userId);
        Files.write(dir.resolve("review_logs.tsv"), logs);
        Files.write(dir.resolve("cards.tsv"), cards);
        Files.writeString(dir.resolve("fsrs_parameters.tsv"), parameters + "\n");
        Files.writeString(dir.resolve("report.txt"), report + "\n");
    }

    private static String format(double[] parameters) {
        return String.format("%.2f / %.2f / %.2f / %.2f", parameters[0], parameters[1], parameters[2], parameters[3]);
    }

    private static String percent(double rate) {
        return String.format("%.1f%%", rate * 100);
    }

    private static String calibration(ReviewLogRepository.Calibration row) {
        return row.getReviews() + " lượt, dự đoán " + percent(row.getPredicted()) + ", thực tế " + percent(row.getActual());
    }
}
