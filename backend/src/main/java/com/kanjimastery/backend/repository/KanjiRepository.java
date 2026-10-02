package com.kanjimastery.backend.repository;

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
            @Param("level") String level,
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
    List<Kanji> findAllByFilters(@Param("level") String level, @Param("tagId") Long tagId);

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
}
