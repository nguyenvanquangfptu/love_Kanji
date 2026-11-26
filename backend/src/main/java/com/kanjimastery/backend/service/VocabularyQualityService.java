package com.kanjimastery.backend.service;

import com.kanjimastery.backend.dto.VocabularyQualityReport;
import com.kanjimastery.backend.dto.VocabularyQualityReport.HanVietConflict;
import com.kanjimastery.backend.dto.VocabularyQualityReport.StrokeMismatch;
import com.kanjimastery.backend.dto.VocabularyQualityReport.Variant;
import com.kanjimastery.backend.dto.VocabularyQualityReport.WordRef;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.repository.KanjiRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Kiểm tra chất lượng kho từ vựng: số nét lệch với bảng số nét từng chữ, chữ Hán được ghi nhiều âm Hán Việt, từ chưa có
 * âm Hán Việt - và sửa hàng loạt số nét. Từ nhập thẳng bằng SQL hay nhập tay trước đây hay sai số nét (外 ghi 9 nét).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VocabularyQualityService {

    private final KanjiRepository kanjiRepository;
    private final StrokeCounter strokeCounter;

    @Transactional(readOnly = true)
    public VocabularyQualityReport report() {
        List<Kanji> words = kanjiRepository.findAll(Sort.by("id"));
        Map<String, Integer> strokes = strokes(words);
        List<StrokeMismatch> mismatches = new ArrayList<>();
        Set<String> unknown = new TreeSet<>();
        List<WordRef> missingHanViet = new ArrayList<>();
        for (Kanji word : words) {
            StrokeCounter.Count count = StrokeCounter.count(word.getCharacter(), strokes);
            if (!count.known()) {
                unknown.addAll(count.unknown());
            } else if (count.total() != word.getStrokeCount()) {
                mismatches.add(new StrokeMismatch(word.getId(), word.getCharacter(), word.getStrokeCount(), count.total()));
            }
            if (!StrokeCounter.kanjiOf(word.getCharacter()).isEmpty() && !StringUtils.hasText(word.getHanViet())) {
                missingHanViet.add(new WordRef(word.getId(), word.getCharacter(), word.getHanViet()));
            }
        }
        return new VocabularyQualityReport(words.size(), mismatches, List.copyOf(unknown), hanVietConflicts(),
                missingHanViet);
    }

    /**
     * Ghi lại số nét theo bảng cho các từ đang lệch; từ có chữ chưa có số nét thì giữ nguyên. {@code dryRun} chỉ đếm.
     */
    @CacheEvict(value = "kanji", allEntries = true, condition = "!#dryRun")
    @Transactional
    public VocabularyQualityReport.StrokeFix fixStrokeCounts(boolean dryRun) {
        List<Kanji> words = kanjiRepository.findAll();
        Map<String, Integer> strokes = strokes(words);
        int changed = 0;
        for (Kanji word : words) {
            StrokeCounter.Count count = StrokeCounter.count(word.getCharacter(), strokes);
            if (count.known() && count.total() != word.getStrokeCount()) {
                changed++;
                if (!dryRun) {
                    word.setStrokeCount(count.total());
                }
            }
        }
        if (!dryRun) {
            log.info("Sửa số nét theo bảng kanji_strokes: {} từ", changed);
        }
        return new VocabularyQualityReport.StrokeFix(dryRun, changed);
    }

    private Map<String, Integer> strokes(List<Kanji> words) {
        return strokeCounter.lookup(words.stream()
                .flatMap(word -> StrokeCounter.kanjiOf(word.getCharacter()).stream())
                .collect(Collectors.toSet()));
    }

    /** Gom các dòng (chữ, âm, từ) đã sắp theo chữ rồi theo âm thành từng chữ kèm các âm của nó. */
    private List<HanVietConflict> hanVietConflicts() {
        Map<String, Map<String, List<WordRef>>> byCharacter = new LinkedHashMap<>();
        for (KanjiRepository.CharacterReading row : kanjiRepository.findCharactersWithSeveralHanViet()) {
            byCharacter.computeIfAbsent(row.getCharacter(), character -> new LinkedHashMap<>())
                    .computeIfAbsent(row.getHanViet(), hanViet -> new ArrayList<>())
                    .add(new WordRef(row.getWordId(), row.getWord(), row.getWordHanViet()));
        }
        return byCharacter.entrySet().stream()
                .map(entry -> new HanVietConflict(entry.getKey(), entry.getValue().entrySet().stream()
                        .map(variant -> new Variant(variant.getKey(), List.copyOf(variant.getValue())))
                        // Âm ít từ dùng nhất lên đầu: thường là âm ghi nhầm.
                        .sorted(Comparator.comparingInt(variant -> variant.words().size()))
                        .toList()))
                .toList();
    }
}
