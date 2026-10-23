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
    @Query("UPDATE Kanji k SET k.exampleSentence = :sentence WHERE k.id = :id AND k.exampleSentence IS NULL")
    int saveExampleSentenceIfAbsent(@Param("id") Long id, @Param("sentence") String sentence);

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
}
