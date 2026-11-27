package com.kanjimastery.backend.repository;

import com.kanjimastery.backend.model.JlptLevel;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import com.kanjimastery.backend.model.Kanji;

public interface KanjiRepository extends JpaRepository<Kanji, Long> {

    @Query("""
            SELECT DISTINCT k FROM Kanji k
            LEFT JOIN k.tags t
            WHERE (:level IS NULL OR k.jlptLevel = :level)
              AND (:pattern IS NULL OR
                   LOWER(k.character) LIKE :pattern OR
                   LOWER(k.hanViet) LIKE :pattern OR
                   LOWER(k.reading) LIKE :pattern OR
                   LOWER(k.meaning) LIKE :pattern)
              AND (:tagId IS NULL OR t.id = :tagId)
            """)
    Page<Kanji> search(
            @Param("level") JlptLevel level,
            @Param("pattern") String pattern,
            @Param("tagId") Long tagId,
            Pageable pageable);

    List<Kanji> findAllByCharacter(String character);

    List<Kanji> findAllByCharacterIn(Collection<String> characters);

    long countByIdIn(Collection<Long> ids);

    long countByTags_Id(Long tagId);

    @Query("""
            SELECT DISTINCT k FROM Kanji k
            LEFT JOIN k.tags t
            WHERE (:level IS NULL OR k.jlptLevel = :level)
              AND (:tagId IS NULL OR t.id = :tagId)
            """)
    List<Kanji> findAllByFilters(@Param("level") JlptLevel level, @Param("tagId") Long tagId);

    /** Từ thuộc các bài có tên bắt đầu bằng {@code prefix} (vd. "N5-%") - một từ có thể nằm ở bài của nhiều cấp độ. */
    @Query("SELECT DISTINCT k FROM Kanji k JOIN k.tags t WHERE t.name LIKE :prefix")
    List<Kanji> findAllByTagNamePrefix(@Param("prefix") String prefix);

    /** Các từ cùng bài (cùng tag) với ít nhất một trong {@code ids}, kể cả chính các từ đó nếu có tag. */
    @Query("""
            SELECT DISTINCT k FROM Kanji k JOIN k.tags t
            WHERE t IN (SELECT t2 FROM Kanji k2 JOIN k2.tags t2 WHERE k2.id IN :ids)
            """)
    List<Kanji> findLessonmatesOf(@Param("ids") Collection<Long> ids);

    /** Gọi từ luồng nền sau khi request quiz đã trả về, nên tự mở transaction riêng. */
    @Transactional
    @Modifying
    @Query("""
            UPDATE Kanji k SET k.exampleSentence = :sentence,
                               k.exampleSentenceSource = com.kanjimastery.backend.model.ExampleSentenceSource.AI
            WHERE k.id = :id AND k.exampleSentence IS NULL
            """)
    int saveExampleSentenceIfAbsent(@Param("id") Long id, @Param("sentence") String sentence);

    /**
     * Từ trong các bài học chưa có câu ví dụ - để AI viết câu dần mỗi đêm. Từ đã có người học đang ôn trước (họ gặp
     * thẻ thiếu câu ngay), rồi cấp độ dễ trước (N5 → N1). Từ chưa gắn bài nào không cần câu (không vào quiz, không sinh
     * câu thi).
     */
    @Query(value = """
            SELECT k.* FROM kanji k
            WHERE k.example_sentence IS NULL
              AND EXISTS (SELECT 1 FROM kanji_tags kt WHERE kt.kanji_id = k.id)
            ORDER BY EXISTS (SELECT 1 FROM user_kanji_srs s WHERE s.kanji_id = k.id) DESC,
                     CASE k.jlpt_level WHEN 'N5' THEN 1 WHEN 'N4' THEN 2 WHEN 'N3' THEN 3 WHEN 'N2' THEN 4 ELSE 5 END,
                     k.id
            LIMIT :limit
            """, nativeQuery = true)
    List<Kanji> findWithoutExampleSentence(@Param("limit") int limit);

    /** Số từ trong bài học còn thiếu câu ví dụ theo cấp độ, và bao nhiêu trong số đó đang có người học ôn. */
    interface MissingSentenceCount {
        String getLevel();

        long getMissing();

        long getInReview();
    }

    @Query(value = """
            SELECT k.jlpt_level AS "level", COUNT(*) AS "missing",
                   COUNT(*) FILTER (WHERE EXISTS (SELECT 1 FROM user_kanji_srs s WHERE s.kanji_id = k.id)) AS "inReview"
            FROM kanji k
            WHERE k.example_sentence IS NULL AND EXISTS (SELECT 1 FROM kanji_tags kt WHERE kt.kanji_id = k.id)
            GROUP BY k.jlpt_level
            ORDER BY k.jlpt_level DESC
            """, nativeQuery = true)
    List<MissingSentenceCount> countWithoutExampleSentenceByLevel();

    @Query(value = """
            SELECT COUNT(*) FROM kanji k
            WHERE k.example_sentence IS NULL AND EXISTS (SELECT 1 FROM kanji_tags kt WHERE kt.kanji_id = k.id)
            """, nativeQuery = true)
    long countWithoutExampleSentence();

    /** Chỉ lưu khi từ chưa có mẹo nhớ, để hai người cùng nhờ AI một lúc vẫn thấy chung một mẹo nhớ. */
    @Transactional
    @Modifying
    @Query("UPDATE Kanji k SET k.mnemonic = :mnemonic WHERE k.id = :id AND k.mnemonic IS NULL")
    int saveMnemonicIfAbsent(@Param("id") Long id, @Param("mnemonic") String mnemonic);

    @Query("SELECT k.mnemonic FROM Kanji k WHERE k.id = :id")
    Optional<String> findMnemonicById(@Param("id") Long id);

    /** Cách viết, cách đọc và cấp độ của một từ. */
    interface WordLevel {
        String getCharacter();

        String getReading();

        JlptLevel getJlptLevel();
    }

    /** Mọi từ trong kho - để kiểm tra một câu có dùng từ vượt cấp độ không. */
    @Query("SELECT k.character AS character, k.reading AS reading, k.jlptLevel AS jlptLevel FROM Kanji k")
    List<WordLevel> findAllWordLevels();

    /** Tìm từ: cách viết khớp mẫu LIKE (đã escape, ký tự escape là \). Tối đa 200 dòng. */
    @Query(value = "SELECT * FROM kanji k WHERE k.character LIKE :pattern ESCAPE '\\' LIMIT 200", nativeQuery = true)
    List<Kanji> searchByWritten(@Param("pattern") String pattern);

    /** Tìm từ: cách đọc (bỏ ngoặc okurigana, み(る) → みる) khớp mẫu LIKE. Tối đa 200 dòng. */
    @Query(value = "SELECT * FROM kanji k WHERE translate(k.reading, '()（）', '') LIKE :pattern ESCAPE '\\' LIMIT 200",
            nativeQuery = true)
    List<Kanji> searchByReading(@Param("pattern") String pattern);

    /** Tìm từ: Hán Việt hoặc nghĩa, không phân biệt hoa thường và dấu (V37), khớp mẫu LIKE. Tối đa 200 dòng. */
    @Query(value = """
            SELECT * FROM kanji k
            WHERE unaccent(lower(k.han_viet)) LIKE :pattern ESCAPE '\\'
               OR unaccent(lower(k.meaning)) LIKE :pattern ESCAPE '\\'
            LIMIT 200
            """, nativeQuery = true)
    List<Kanji> searchByHanVietOrMeaning(@Param("pattern") String pattern);

    /** Tên bài (tag) của một từ. */
    interface WordLesson {
        Long getWordId();

        String getName();
    }

    @Query("SELECT k.id AS wordId, t.name AS name FROM Kanji k JOIN k.tags t WHERE k.id IN :wordIds ORDER BY t.name")
    List<WordLesson> findLessonsOf(@Param("wordIds") Collection<Long> wordIds);

    /** Mọi từ kèm các chữ Hán thành phần, trong một truy vấn - để tách lại cả kho. */
    @Query("SELECT k FROM Kanji k LEFT JOIN FETCH k.characterParts")
    List<Kanji> findAllWithCharacterParts();

    /** Một từ có chứa một chữ Hán, kèm âm Hán Việt của chữ đó trong từ ấy. */
    interface CharacterUse {
        Long getWordId();

        String getHanViet();
    }

    @Query("SELECT k.id AS wordId, c.hanViet AS hanViet FROM Kanji k JOIN k.characterParts c WHERE c.character = :character")
    List<CharacterUse> findUsesOfCharacter(@Param("character") String character);

    /** Một chữ Hán thành phần của một từ. */
    interface CharacterPart {
        Long getWordId();

        String getCharacter();

        String getHanViet();
    }

    /** Các chữ Hán thành phần của các từ, theo thứ tự trong từng từ. */
    @Query("""
            SELECT k.id AS wordId, c.character AS character, c.hanViet AS hanViet
            FROM Kanji k JOIN k.characterParts c
            WHERE k.id IN :wordIds
            ORDER BY k.id, INDEX(c)
            """)
    List<CharacterPart> findCharacterPartsOf(@Param("wordIds") Collection<Long> wordIds);

    /** Một chữ Hán thành phần của một từ, kèm cách viết của từ - để báo các chữ được ghi nhiều âm Hán Việt. */
    interface CharacterReading {
        String getCharacter();

        String getHanViet();

        Long getWordId();

        String getWord();

        String getWordHanViet();
    }

    /** Các chữ Hán được ghi nhiều hơn một âm Hán Việt (込 VÀO / NHẬP...) và các từ dùng từng âm. */
    @Query(value = """
            SELECT wc.character AS "character", wc.han_viet AS "hanViet", k.id AS "wordId", k.character AS "word",
                   k.han_viet AS "wordHanViet"
            FROM word_characters wc
            JOIN kanji k ON k.id = wc.word_id
            WHERE wc.character IN (SELECT character FROM word_characters GROUP BY character
                                   HAVING COUNT(DISTINCT han_viet) > 1)
            ORDER BY wc.character, wc.han_viet, k.id
            """, nativeQuery = true)
    List<CharacterReading> findCharactersWithSeveralHanViet();
}
