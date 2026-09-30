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
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.kanjimastery.backend.model.UserKanjiSrs;
import com.kanjimastery.backend.repository.UserKanjiSrsRepository;

@Service
@RequiredArgsConstructor
public class SrsService {

    private static final int MASTERED_INTERVAL_DAYS_THRESHOLD = 21;

    private final UserKanjiSrsRepository srsRepository;
    private final KanjiRepository kanjiRepository;
    private final SrsCalculatorService srsCalculatorService;

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

        UserKanjiSrs srs = srsRepository.findByUserIdAndKanjiId(userId, request.getKanjiId())
                .orElseGet(() -> UserKanjiSrs.builder()
                        .userId(userId)
                        .kanjiId(request.getKanjiId())
                        .repetitionCount(0)
                        .easinessFactor(new BigDecimal("2.50"))
                        .reviewIntervalDays(0)
                        .nextReviewAt(LocalDateTime.now())
                        .build());

        SrsCalculatorService.SrsResult result = srsCalculatorService.calculateNext(
                srs.getRepetitionCount(), srs.getEasinessFactor(), srs.getReviewIntervalDays(), request.getQuality());

        LocalDateTime now = LocalDateTime.now();
        srs.setRepetitionCount(result.repetitionCount());
        srs.setEasinessFactor(result.easinessFactor());
        srs.setReviewIntervalDays(result.reviewIntervalDays());
        srs.setNextReviewAt(now.plusDays(result.reviewIntervalDays()));
        srs.setLastReviewedAt(now);

        UserKanjiSrs saved = srsRepository.save(srs);

        return ReviewResponse.builder()
                .kanjiId(saved.getKanjiId())
                .repetitionCount(saved.getRepetitionCount())
                .easinessFactor(saved.getEasinessFactor())
                .reviewIntervalDays(saved.getReviewIntervalDays())
                .nextReviewAt(saved.getNextReviewAt())
                .build();
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
