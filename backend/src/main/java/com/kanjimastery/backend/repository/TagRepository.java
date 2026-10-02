package com.kanjimastery.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.kanjimastery.backend.model.Tag;

public interface TagRepository extends JpaRepository<Tag, Long> {

    boolean existsByName(String name);

    /** Mỗi phần tử: [tagId (Long), số Kanji gắn tag đó (Long)] - tag không có Kanji nào sẽ không xuất hiện. */
    @Query("SELECT t.id, COUNT(k.id) FROM Kanji k JOIN k.tags t GROUP BY t.id")
    List<Object[]> countKanjiPerTag();

    /** Số từ trong các bài của những cấp độ cho trước, và số từ trong đó người học đã học ít nhất một lần. */
    interface ScopeProgress {
        long getTotal();

        long getStarted();
    }

    /** Bài thuộc cấp độ nào lấy từ tiền tố tên bài (N4-27 thuộc N4); bài không theo mẫu đó không được tính. */
    @Query(value = """
            SELECT COUNT(DISTINCT kt.kanji_id) AS "total",
                   COUNT(DISTINCT kt.kanji_id) FILTER (WHERE s.last_reviewed_at IS NOT NULL) AS "started"
            FROM kanji_tags kt
            JOIN tags t ON t.id = kt.tag_id
            LEFT JOIN user_kanji_srs s ON s.kanji_id = kt.kanji_id AND s.user_id = :userId
            WHERE split_part(t.name, '-', 1) IN (:levels)
            """, nativeQuery = true)
    ScopeProgress scopeProgress(@Param("userId") Long userId, @Param("levels") Collection<String> levels);

    /** Một bài còn từ chưa có trong Ôn tập của người học. */
    interface LessonToAdd {
        Long getTagId();

        String getName();

        /** Số từ của bài chưa có trong Ôn tập. */
        long getWords();
    }

    /** Bài đầu tiên (N5 trước N4..., trong cùng cấp độ theo số bài) còn từ chưa có trong Ôn tập của người học. */
    @Query(value = """
            SELECT t.id AS "tagId", t.name AS "name", COUNT(*) AS "words"
            FROM tags t
            JOIN kanji_tags kt ON kt.tag_id = t.id
            WHERE split_part(t.name, '-', 1) IN (:levels)
              AND NOT EXISTS (SELECT 1 FROM user_kanji_srs s WHERE s.user_id = :userId AND s.kanji_id = kt.kanji_id)
            GROUP BY t.id, t.name
            ORDER BY CASE split_part(t.name, '-', 1)
                         WHEN 'N5' THEN 1 WHEN 'N4' THEN 2 WHEN 'N3' THEN 3 WHEN 'N2' THEN 4 ELSE 5 END,
                     t.name
            LIMIT 1
            """, nativeQuery = true)
    Optional<LessonToAdd> nextLessonToAdd(@Param("userId") Long userId, @Param("levels") Collection<String> levels);
}
