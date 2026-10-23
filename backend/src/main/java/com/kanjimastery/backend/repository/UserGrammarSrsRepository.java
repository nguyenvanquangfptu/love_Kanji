package com.kanjimastery.backend.repository;

import com.kanjimastery.backend.model.UserGrammarSrs;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserGrammarSrsRepository extends JpaRepository<UserGrammarSrs, Long> {

    Optional<UserGrammarSrs> findByUserIdAndGrammarPointId(Long userId, Long grammarPointId);

    List<UserGrammarSrs> findAllByUserIdAndGrammarPointIdIn(Long userId, Collection<Long> grammarPointIds);

    /** Thẻ đã ôn ít nhất một lần và đã đến hạn (dùng index idx_grammar_srs_user_next_review). */
    List<UserGrammarSrs> findByUserIdAndLastReviewedAtIsNotNullAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc(
            Long userId, LocalDateTime now, Pageable pageable);

    /** Điểm đã thêm nhưng chưa ôn lần nào, theo thứ tự thêm vào. */
    List<UserGrammarSrs> findByUserIdAndLastReviewedAtIsNullOrderByIdAsc(Long userId, Pageable pageable);

    long countByUserIdAndLastReviewedAtIsNotNullAndNextReviewAtLessThanEqual(Long userId, LocalDateTime now);

    long countByUserIdAndLastReviewedAtIsNull(Long userId);

    /**
     * Thêm các điểm ngữ pháp vào lịch ôn, đến hạn ngay. Điểm đã có giữ nguyên tiến độ và id không tồn tại bị bỏ qua, nên
     * bấm lại nhiều lần (hay hai lần cùng lúc) vẫn an toàn. Trả về số điểm thực sự được thêm.
     */
    @Modifying
    @Query(value = """
            INSERT INTO user_grammar_srs (user_id, grammar_point_id, next_review_at)
            SELECT :userId, g.id, :now FROM grammar_points g WHERE g.id IN (:grammarPointIds)
            ORDER BY g.lesson NULLS LAST, g.id
            ON CONFLICT (user_id, grammar_point_id) DO NOTHING
            """, nativeQuery = true)
    int insertCardsIfAbsent(@Param("userId") Long userId,
                            @Param("grammarPointIds") Collection<Long> grammarPointIds,
                            @Param("now") LocalDateTime now);
}
