package com.kanjimastery.backend.service;

import com.kanjimastery.backend.exception.ResourceNotFoundException;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.repository.KanjiRepository;
import com.kanjimastery.backend.dto.KanjiResponse;
import com.kanjimastery.backend.dto.DailyCardResponse;
import com.kanjimastery.backend.dto.ReviewRequest;
import com.kanjimastery.backend.dto.ReviewResponse;
import com.kanjimastery.backend.dto.SrsStatsResponse;
import com.kanjimastery.backend.dto.SrsTagStatusResponse;
import com.kanjimastery.backend.dto.AddSrsCardsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.kanjimastery.backend.model.CardState;
import com.kanjimastery.backend.model.ReviewLog;
import com.kanjimastery.backend.model.ReviewRating;
import com.kanjimastery.backend.model.ReviewSource;
import com.kanjimastery.backend.model.UserKanjiSrs;
import com.kanjimastery.backend.repository.ReviewLogRepository;
import com.kanjimastery.backend.repository.UserKanjiSrsRepository;

@Service
@RequiredArgsConstructor
public class SrsService {

    /** Khoảng ôn từ chừng này ngày trở lên thì coi là "đã thuộc". */
    static final int MASTERED_INTERVAL_DAYS_THRESHOLD = 21;

    private final UserKanjiSrsRepository srsRepository;
    private final KanjiRepository kanjiRepository;
    private final SrsCalculatorService srsCalculatorService;
    private final ReviewLogRepository reviewLogRepository;

    /**
     * Một lần người học trả lời một từ.
     *
     * @param source       {@link ReviewSource}
     * @param direction    hướng hỏi của trắc nghiệm, null với thẻ ôn tập
     * @param rating       {@link ReviewRating}
     * @param responseMs   đã qua {@link ResponseTimeRater#normalize(Integer)}
     * @param chosenAnswer đáp án đã chọn trong trắc nghiệm, null với thẻ ôn tập
     */
    public record Answer(String source, String direction, boolean correct, int rating, Integer responseMs,
                         String chosenAnswer) {
    }

    public Page<DailyCardResponse> getDailyCards(Long userId, Pageable pageable) {
        LocalDateTime now = LocalDateTime.now();
        Page<UserKanjiSrs> duePage = srsRepository
                .findByUserIdAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc(userId, now, pageable);

        List<Long> kanjiIds = duePage.getContent().stream().map(UserKanjiSrs::getKanjiId).toList();
        // Batch-fetch 1 lần duy nhất thay vì gọi findById trong vòng lặp (tránh N+1 Query).
        Map<Long, Kanji> kanjiById = kanjiRepository.findAllById(kanjiIds).stream()
                .collect(Collectors.toMap(Kanji::getId, Function.identity()));

        return duePage.map(srs -> DailyCardResponse.builder()
                .srsId(srs.getId())
                .kanji(KanjiResponse.from(kanjiById.get(srs.getKanjiId())))
                .repetitionCount(srs.getRepetitionCount())
                .easinessFactor(srs.getEasinessFactor())
                .reviewIntervalDays(srs.getReviewIntervalDays())
                .nextReviewAt(srs.getNextReviewAt())
                .lastReviewedAt(srs.getLastReviewedAt())
                .build());
    }

    @Transactional
    public ReviewResponse submitReview(Long userId, ReviewRequest request) {
        if (!kanjiRepository.existsById(request.getKanjiId())) {
            throw new ResourceNotFoundException("Không tìm thấy Kanji với id: " + request.getKanjiId());
        }

        int rating = request.getRating();
        Answer answer = new Answer(ReviewSource.FLASHCARD, null, rating != ReviewRating.AGAIN, rating,
                ResponseTimeRater.normalize(request.getResponseMs()), null);
        UserKanjiSrs card = srsRepository.findByUserIdAndKanjiId(userId, request.getKanjiId()).orElse(null);
        UserKanjiSrs saved = schedule(userId, request.getKanjiId(), card, answer, LocalDateTime.now());

        return ReviewResponse.builder()
                .kanjiId(saved.getKanjiId())
                .repetitionCount(saved.getRepetitionCount())
                .easinessFactor(saved.getEasinessFactor())
                .reviewIntervalDays(saved.getReviewIntervalDays())
                .nextReviewAt(saved.getNextReviewAt())
                .build();
    }

    /**
     * Ghi một câu trắc nghiệm và cập nhật lịch ôn:
     * <ul>
     *   <li>Sai, từ chưa có trong lịch ôn: thêm vào, đến hạn ngay để học trong phiên ôn hôm nay.</li>
     *   <li>Sai, từ đã có: tính là một lần "Quên" - học lại từ đầu như khi lật thẻ.</li>
     *   <li>Đúng, thẻ đã đến hạn: tính là một lần ôn với {@link Answer#rating()}.</li>
     *   <li>Đúng, thẻ chưa đến hạn (hoặc chưa có trong lịch ôn): chỉ ghi lại. SM-2 không tính tới việc ôn sớm,
     *       nên tính vào lịch sẽ nhân khoảng ôn lên dù mới ôn hôm qua.</li>
     * </ul>
     *
     * @return lần ôn tiếp theo, rỗng nếu từ không nằm trong lịch ôn
     */
    @Transactional
    public Optional<LocalDateTime> recordQuizAnswer(Long userId, Long kanjiId, Answer answer) {
        LocalDateTime now = LocalDateTime.now();
        UserKanjiSrs card = srsRepository.findByUserIdAndKanjiId(userId, kanjiId).orElse(null);
        if (card == null) {
            log(userId, kanjiId, null, answer, false, now);
            if (answer.correct()) {
                return Optional.empty();
            }
            srsRepository.insertCardsIfAbsent(userId, Set.of(kanjiId), now);
            return Optional.of(now);
        }
        if (!answer.correct() || !card.getNextReviewAt().isAfter(now)) {
            return Optional.of(schedule(userId, kanjiId, card, answer, now).getNextReviewAt());
        }
        log(userId, kanjiId, card, answer, false, now);
        return Optional.of(card.getNextReviewAt());
    }

    /** Tính lịch ôn mới theo SM-2 (thẻ chưa có thì tạo) và ghi lại lần trả lời trong cùng transaction. */
    private UserKanjiSrs schedule(Long userId, Long kanjiId, UserKanjiSrs card, Answer answer, LocalDateTime now) {
        log(userId, kanjiId, card, answer, true, now);

        UserKanjiSrs srs = card != null ? card : UserKanjiSrs.builder()
                .userId(userId)
                .kanjiId(kanjiId)
                .repetitionCount(0)
                .easinessFactor(new BigDecimal("2.50"))
                .reviewIntervalDays(0)
                .nextReviewAt(now)
                .build();

        SrsCalculatorService.SrsResult result = srsCalculatorService.calculateNext(srs.getRepetitionCount(),
                srs.getEasinessFactor(), srs.getReviewIntervalDays(), ReviewRating.toSm2Quality(answer.rating()));

        srs.setRepetitionCount(result.repetitionCount());
        srs.setEasinessFactor(result.easinessFactor());
        srs.setReviewIntervalDays(result.reviewIntervalDays());
        srs.setNextReviewAt(now.plusDays(result.reviewIntervalDays()));
        srs.setLastReviewedAt(now);
        return srsRepository.save(srs);
    }

    /** Ghi kèm trạng thái thẻ ngay trước lần trả lời ({@code card} null = chưa có trong lịch ôn). */
    private void log(Long userId, Long kanjiId, UserKanjiSrs card, Answer answer, boolean scheduled, LocalDateTime now) {
        reviewLogRepository.save(ReviewLog.builder()
                .userId(userId)
                .kanjiId(kanjiId)
                .source(answer.source())
                .direction(answer.direction())
                .correct(answer.correct())
                .rating((short) answer.rating())
                .responseMs(answer.responseMs())
                .chosenAnswer(answer.chosenAnswer())
                .stateBefore(CardState.of(card))
                .efBefore(card == null ? null : card.getEasinessFactor())
                .intervalBefore(card == null ? null : card.getReviewIntervalDays())
                .scheduled(scheduled)
                .reviewedAt(now)
                .build());
    }

    /**
     * Đưa các từ (cả một bài, hoặc các từ làm sai trong trắc nghiệm) vào lịch ôn SRS.
     * Từ mới đến hạn ôn ngay; từ đã có trong lịch ôn giữ nguyên tiến độ; id không tồn tại bị bỏ qua.
     */
    @Transactional
    public AddSrsCardsResponse addCards(Long userId, List<Long> kanjiIds) {
        Set<Long> uniqueIds = new HashSet<>(kanjiIds);
        long existingWords = kanjiRepository.countByIdIn(uniqueIds);
        int added = srsRepository.insertCardsIfAbsent(userId, uniqueIds, LocalDateTime.now());
        return AddSrsCardsResponse.builder()
                .added(added)
                .alreadyInReview((int) existingWords - added)
                .build();
    }

    public SrsTagStatusResponse getTagStatus(Long userId, Long tagId) {
        return SrsTagStatusResponse.builder()
                .totalWords(kanjiRepository.countByTags_Id(tagId))
                .inReview(srsRepository.countInReviewByTag(userId, tagId))
                .build();
    }

    public SrsStatsResponse getStats(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        long total = srsRepository.countByUserId(userId);
        long due = srsRepository.countByUserIdAndNextReviewAtLessThanEqual(userId, now);
        long mastered = srsRepository.countByUserIdAndNextReviewAtAfterAndReviewIntervalDaysGreaterThanEqual(
                userId, now, MASTERED_INTERVAL_DAYS_THRESHOLD);
        long learning = total - due - mastered;

        return SrsStatsResponse.builder()
                .totalCardsStarted(total)
                .dueForReview(due)
                .stillLearning(Math.max(learning, 0))
                .deeplyMemorized(mastered)
                .build();
    }
}
