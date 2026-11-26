package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.KanjiStroke;
import com.kanjimastery.backend.repository.KanjiStrokeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Số nét của một từ = tổng số nét các chữ Hán trong từ (bảng kanji_strokes), kana không tính. 々 có trong bảng (3 nét)
 * nên 人々 = 2 + 3. Chữ chưa có trong bảng thì không tính được - người nhập phải tự ghi số nét.
 */
@Component
@RequiredArgsConstructor
public class StrokeCounter {

    private final KanjiStrokeRepository strokeRepository;

    /**
     * @param total   tổng số nét (chỉ đúng khi {@code unknown} rỗng)
     * @param unknown các chữ Hán chưa có số nét trong bảng, theo thứ tự xuất hiện
     */
    public record Count(int total, List<String> unknown) {

        public boolean known() {
            return unknown.isEmpty();
        }
    }

    /** Số nét của một từ, đọc bảng số nét cho riêng các chữ của từ đó. */
    public Count count(String word) {
        return count(word, lookup(kanjiOf(word)));
    }

    /** Số nét của các chữ Hán {@code characters} có trong bảng, trong một truy vấn - để tính cho nhiều từ một lúc. */
    public Map<String, Integer> lookup(Collection<String> characters) {
        if (characters.isEmpty()) {
            return Map.of();
        }
        return strokeRepository.findAllByCharacterIn(characters).stream()
                .collect(Collectors.toMap(KanjiStroke::getCharacter, KanjiStroke::getStrokeCount));
    }

    /** Số nét của {@code word} theo bảng {@code strokes} đã đọc sẵn. */
    public static Count count(String word, Map<String, Integer> strokes) {
        int total = 0;
        Set<String> unknown = new LinkedHashSet<>();
        for (String kanji : kanjiList(word)) {
            Integer strokeCount = strokes.get(kanji);
            if (strokeCount == null) {
                unknown.add(kanji);
            } else {
                total += strokeCount;
            }
        }
        return new Count(total, List.copyOf(unknown));
    }

    /** Các chữ Hán (kể cả 々) khác nhau trong từ. */
    public static Set<String> kanjiOf(String word) {
        return new LinkedHashSet<>(kanjiList(word));
    }

    private static List<String> kanjiList(String word) {
        return word == null ? List.of() : word.codePoints()
                .filter(QuizDistractorGenerator::isKanji)
                .mapToObj(Character::toString)
                .toList();
    }
}
