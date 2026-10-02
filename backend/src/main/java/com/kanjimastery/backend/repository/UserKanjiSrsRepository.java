package com.kanjimastery.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import com.kanjimastery.backend.model.UserKanjiSrs;

public interface UserKanjiSrsRepository extends JpaRepository<UserKanjiSrs, Long> {

    Optional<UserKanjiSrs> findByUserIdAndKanjiId(Long userId, Long kanjiId);

    List<UserKanjiSrs> findAllByUserIdAndKanjiIdIn(Long userId, Collection<Long> kanjiIds);

    // Tận dụng composite index idx_user_next_review (user_id, next_review_at)
    Page<UserKanjiSrs> findByUserIdAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc(
            Long userId, LocalDateTime now, Pageable pageable);

    long countByUserId(Long userId);

    long countByUserIdAndLapseCountGreaterThanEqual(Long userId, Integer minLapses);

    List<UserKanjiSrs> findByUserIdAndLapseCountGreaterThanEqualOrderByLapseCountDesc(Long userId, Integer minLapses);

    long countByUserIdAndNextReviewAtLessThanEqual(Long userId, LocalDateTime now);

    long countByUserIdAndNextReviewAtAfterAndReviewIntervalDaysGreaterThanEqual(
            Long userId, LocalDateTime now, Integer minIntervalDays);

    /**
     * Đưa các từ vào lịch ôn, đến hạn ngay tại {@code now}. Từ đã có trong lịch ôn (giữ nguyên tiến độ)
     * và id không tồn tại đều được bỏ qua, nên bấm lại nhiều lần vẫn an toàn. Trả về số từ thực sự được thêm.
     */
    @Modifying
    @Query(value = """
            INSERT INTO user_kanji_srs (user_id, kanji_id, repetition_count, easiness_factor, review_interval_days, next_review_at)
            SELECT :userId, k.id, 0, 2.50, 0, :now FROM kanji k WHERE k.id IN (:kanjiIds)
            ON CONFLICT (user_id, kanji_id) DO NOTHING
            """, nativeQuery = true)
    int insertCardsIfAbsent(@Param("userId") Long userId,
                            @Param("kanjiIds") Collection<Long> kanjiIds,
                            @Param("now") LocalDateTime now);

    @Query(value = """
            SELECT COUNT(*) FROM kanji_tags kt
            JOIN user_kanji_srs s ON s.kanji_id = kt.kanji_id AND s.user_id = :userId
            WHERE kt.tag_id = :tagId
            """, nativeQuery = true)
    long countInReviewByTag(@Param("userId") Long userId, @Param("tagId") Long tagId);
}
