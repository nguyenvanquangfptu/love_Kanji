package com.kanjimastery.backend.service;

import com.kanjimastery.backend.dto.SearchResultResponse;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.model.UserKanjiSrs;
import com.kanjimastery.backend.repository.KanjiRepository;
import com.kanjimastery.backend.repository.UserKanjiSrsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Tìm từ trong kho cho người học, gõ bằng gì cũng được: chữ Hán (学生), kana (がくせい, ガクセイ), romaji (gakusei), Hán
 * Việt hay nghĩa - có dấu hay không dấu (hoc sinh, bac si). Chuỗi chữ Latin vừa có thể là romaji vừa có thể là Hán
 * Việt (sinh) nên tìm theo cả hai rồi gộp lại. Kết quả xếp: khớp đúng cả từ, rồi khớp phần đầu, rồi chứa ở giữa, nghĩa
 * sau cùng; cùng hạng thì từ cấp dễ (N5) trước.
 */
@Service
@RequiredArgsConstructor
public class SearchService {

    static final int MAX_RESULTS = 30;
    private static final int MAX_QUERY_LENGTH = 50;
    /** Chuỗi có thể là romaji: chỉ chữ Latin không dấu, dấu nháy (han'i), gạch nối (ー) và khoảng trắng. */
    private static final Pattern ROMAJI = Pattern.compile("[A-Za-z' -]+");
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern MEANING_PARTS = Pattern.compile("[,;()\\[\\]]");

    private final KanjiRepository kanjiRepository;
    private final UserKanjiSrsRepository srsRepository;

    @Transactional(readOnly = true)
    public List<SearchResultResponse> search(Long userId, String input) {
        Query query = Query.of(input);
        if (query == null) {
            return List.of();
        }
        Map<Long, Kanji> found = new LinkedHashMap<>();
        if (query.japanese()) {
            kanjiRepository.searchByWritten(contains(query.text())).forEach(word -> found.putIfAbsent(word.getId(), word));
            kanjiRepository.searchByReading(contains(query.kana())).forEach(word -> found.putIfAbsent(word.getId(), word));
        } else {
            kanjiRepository.searchByHanVietOrMeaning(contains(query.latin()))
                    .forEach(word -> found.putIfAbsent(word.getId(), word));
            for (String kana : query.romaji()) {
                kanjiRepository.searchByReading(contains(kana)).forEach(word -> found.putIfAbsent(word.getId(), word));
            }
        }
        List<Kanji> best = found.values().stream()
                .sorted(Comparator.comparingInt((Kanji word) -> rank(word, query))
                        .thenComparing(Kanji::getJlptLevel)
                        .thenComparingInt(word -> word.getCharacter().length())
                        .thenComparing(Kanji::getId))
                .limit(MAX_RESULTS)
                .toList();
        if (best.isEmpty()) {
            return List.of();
        }

        List<Long> ids = best.stream().map(Kanji::getId).toList();
        Map<Long, List<String>> lessons = kanjiRepository.findLessonsOf(ids).stream()
                .collect(Collectors.groupingBy(KanjiRepository.WordLesson::getWordId,
                        Collectors.mapping(KanjiRepository.WordLesson::getName, Collectors.toList())));
        Set<Long> inReview = srsRepository.findAllByUserIdAndKanjiIdIn(userId, ids).stream()
                .map(UserKanjiSrs::getKanjiId).collect(Collectors.toSet());
        return best.stream()
                .map(word -> new SearchResultResponse(word.getId(), word.getCharacter(), word.getReading(),
                        word.getHanViet(), word.getMeaning(), word.getJlptLevel().name(),
                        lessons.getOrDefault(word.getId(), List.of()), inReview.contains(word.getId())))
                .toList();
    }

    /** 0 khớp đúng cả từ, 1 khớp phần đầu (hoặc đúng một ý của nghĩa), 2 chứa ở giữa, 3 chỉ có trong nghĩa. */
    static int rank(Kanji word, Query query) {
        String reading = word.getReading() == null ? ""
                : RomajiConverter.katakanaToHiragana(ReadingMatcher.displayReading(word.getReading()));
        if (query.japanese()) {
            if (word.getCharacter().equals(query.text()) || reading.equals(query.kana())) {
                return 0;
            }
            return word.getCharacter().startsWith(query.text()) || reading.startsWith(query.kana()) ? 1 : 2;
        }
        String hanViet = unaccent(word.getHanViet());
        if (hanViet.equals(query.latin()) || query.romaji().contains(reading)) {
            return 0;
        }
        if (hanViet.startsWith(query.latin()) || query.romaji().stream().anyMatch(reading::startsWith)) {
            return 1;
        }
        boolean wholeMeaning = MEANING_PARTS.splitAsStream(unaccent(word.getMeaning()))
                .anyMatch(part -> part.strip().equals(query.latin()));
        if (wholeMeaning) {
            return 1;
        }
        return hanViet.contains(query.latin()) || query.romaji().stream().anyMatch(reading::contains) ? 2 : 3;
    }

    /** Như unaccent(lower(...)) của PostgreSQL: bỏ dấu tiếng Việt (kể cả đ → d), chữ thường. */
    static String unaccent(String text) {
        if (text == null) {
            return "";
        }
        String bare = MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        return bare.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
    }

    /** Mẫu LIKE "chứa chuỗi", escape các ký tự đặc biệt của LIKE bằng \. */
    private static String contains(String text) {
        return "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    /**
     * Chuỗi người học gõ, đã chuẩn hoá.
     *
     * @param text     nguyên văn (bỏ khoảng trắng hai đầu)
     * @param japanese có chữ Hán hoặc kana: tìm theo cách viết và cách đọc
     * @param kana     {@code text} với katakana đổi sang hiragana, để so với cách đọc
     * @param latin    bỏ dấu, chữ thường, để so với Hán Việt và nghĩa
     * @param romaji   các cách đọc hiragana nếu {@code text} là romaji đọc được; rỗng nếu không
     */
    record Query(String text, boolean japanese, String kana, String latin, List<String> romaji) {

        static Query of(String input) {
            if (input == null) {
                return null;
            }
            String text = Normalizer.normalize(input, Normalizer.Form.NFC).strip().replaceAll("\\s+", " ");
            if (text.isEmpty()) {
                return null;
            }
            if (text.length() > MAX_QUERY_LENGTH) {
                text = text.substring(0, MAX_QUERY_LENGTH);
            }
            boolean japanese = text.codePoints().anyMatch(Query::isJapanese);
            List<String> romaji = !japanese && ROMAJI.matcher(text).matches()
                    ? new ArrayList<>(RomajiConverter.toHiragana(text)) : List.of();
            return new Query(text, japanese, RomajiConverter.katakanaToHiragana(text), unaccent(text), romaji);
        }

        private static boolean isJapanese(int codePoint) {
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            return script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA;
        }
    }
}
