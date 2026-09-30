package com.kanjimastery.backend.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.kanjimastery.backend.model.Tag;

public interface TagRepository extends JpaRepository<Tag, Long> {

    boolean existsByName(String name);

    /** Mỗi phần tử: [tagId (Long), số Kanji gắn tag đó (Long)] - tag không có Kanji nào sẽ không xuất hiện. */
    @Query("SELECT t.id, COUNT(k.id) FROM Kanji k JOIN k.tags t GROUP BY t.id")
    List<Object[]> countKanjiPerTag();
}
