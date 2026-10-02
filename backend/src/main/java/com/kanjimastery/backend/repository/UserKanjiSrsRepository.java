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

    /** Thẻ ôn đến hạn (đã học ít nhất một lần - không tính từ mới đang chờ). */
    long countByUserIdAndLastReviewedAtIsNotNullAndNextReviewAtLessThanEqual(Long userId, LocalDateTime now);

    /** Từ mới đã đưa vào Ôn tập nhưng chưa học lần nào. */
    long countByUserIdAndLastReviewedAtIsNull(Long userId);

    /** Thẻ đã học sẽ đến hạn trong khoảng thời gian cho trước - để ước lượng lượng ôn những ngày tới. */
    long countByUserIdAndLastReviewedAtIsNotNullAndNextReviewAtBetween(Long userId, LocalDateTime from, LocalDateTime to);

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

    /**
     * Thêm một từ người học vừa làm sai trong trắc nghiệm: từ đã gặp và vừa quên chứ không phải từ mới, nên ghi
     * {@code now} là lần ôn gần nhất, đến hạn ôn ngay. Từ đã có trong lịch ôn được bỏ qua.
     */
    @Modifying
    @Query(value = """
            INSERT INTO user_kanji_srs (user_id, kanji_id, repetition_count, easiness_factor, review_interval_days,
                                        next_review_at, last_reviewed_at)
            VALUES (:userId, :kanjiId, 0, 2.50, 0, :now, :now)
            ON CONFLICT (user_id, kanji_id) DO NOTHING
            """, nativeQuery = true)
    int insertSeenCardIfAbsent(@Param("userId") Long userId,
                               @Param("kanjiId") Long kanjiId,
                               @Param("now") LocalDateTime now);

    @Query(value = """
            SELECT COUNT(*) FROM kanji_tags kt
            JOIN user_kanji_srs s ON s.kanji_id = kt.kanji_id AND s.user_id = :userId
            WHERE kt.tag_id = :tagId
            """, nativeQuery = true)
    long countInReviewByTag(@Param("userId") Long userId, @Param("tagId") Long tagId);
}
