package com.kanjimastery.backend.service;

import com.kanjimastery.backend.dto.CharacterDetailResponse;
import com.kanjimastery.backend.dto.CharacterRebuildReport;
import com.kanjimastery.backend.dto.WordCharacterResponse;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.exception.ResourceNotFoundException;
import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.model.UserKanjiSrs;
import com.kanjimastery.backend.repository.KanjiRepository;
import com.kanjimastery.backend.repository.UserKanjiSrsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Hán Việt từng chữ của các từ vựng (bảng word_characters). Từ thêm/sửa qua trang quản trị được tách ngay trong
 * {@link KanjiService}; từ nhập thẳng vào DB bằng SQL thì cần chạy {@link #rebuild(boolean)}.
 */
@Service
@RequiredArgsConstructor
public class WordCharacterService {

    /** Số từ tối đa trong một lần hỏi các chữ thành phần (một trang danh sách từ). */
    static final int MAX_WORD_IDS = 200;

    private final KanjiRepository kanjiRepository;
    private final UserKanjiSrsRepository srsRepository;

    /**
     * Các chữ Hán thành phần của từng từ, theo thứ tự trong từ. Từ không tách được (hoặc không có chữ Hán) không có
     * trong kết quả - giao diện hiện âm Hán Việt của cả từ như cũ.
     */
    @Transactional(readOnly = true)
    public Map<Long, List<WordCharacterResponse>> partsOf(Collection<Long> wordIds) {
        Set<Long> ids = wordIds.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.size() > MAX_WORD_IDS) {
            throw new BadRequestException("Mỗi lần hỏi tối đa " + MAX_WORD_IDS + " từ");
        }
        Map<Long, List<WordCharacterResponse>> parts = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return parts;
        }
        for (KanjiRepository.CharacterPart part : kanjiRepository.findCharacterPartsOf(ids)) {
            parts.computeIfAbsent(part.getWordId(), id -> new ArrayList<>())
                    .add(new WordCharacterResponse(part.getCharacter(), part.getHanViet()));
        }
        return parts;
    }

    /**
     * Một chữ Hán và các từ trong kho chứa nó, gom theo âm Hán Việt của chữ trong từng từ; âm có nhiều từ đứng trước,
     * trong mỗi âm thì từ cấp độ dễ (N5) trước.
     */
    @Transactional(readOnly = true)
    public CharacterDetailResponse detail(Long userId, String character) {
        if (character == null || character.codePointCount(0, character.length()) != 1
                || !QuizDistractorGenerator.isKanji(character.codePointAt(0))) {
            throw new BadRequestException("Chỉ tra được một chữ Hán");
        }
        Map<String, Set<Long>> idsByReading = new LinkedHashMap<>();
        for (KanjiRepository.CharacterUse use : kanjiRepository.findUsesOfCharacter(character)) {
            idsByReading.computeIfAbsent(use.getHanViet(), reading -> new LinkedHashSet<>()).add(use.getWordId());
        }
        if (idsByReading.isEmpty()) {
            throw new ResourceNotFoundException("Chưa có từ nào chứa chữ " + character);
        }
        Set<Long> allIds = idsByReading.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
        Map<Long, Kanji> words = kanjiRepository.findAllById(allIds).stream()
                .collect(Collectors.toMap(Kanji::getId, Function.identity()));
        Set<Long> inReview = srsRepository.findAllByUserIdAndKanjiIdIn(userId, allIds).stream()
                .map(UserKanjiSrs::getKanjiId).collect(Collectors.toSet());

        Comparator<CharacterDetailResponse.Word> easiestFirst = Comparator
                .comparing((CharacterDetailResponse.Word word) -> JlptLevel.valueOf(word.jlptLevel()))
                .thenComparing(CharacterDetailResponse.Word::character);
        List<CharacterDetailResponse.Reading> readings = idsByReading.entrySet().stream()
                .map(entry -> new CharacterDetailResponse.Reading(entry.getKey(), entry.getValue().stream()
                        .map(words::get).filter(Objects::nonNull)
                        .map(word -> new CharacterDetailResponse.Word(word.getId(), word.getCharacter(),
                                word.getReading(), word.getHanViet(), word.getMeaning(), word.getJlptLevel().name(),
                                inReview.contains(word.getId())))
                        .sorted(easiestFirst)
                        .toList()))
                .sorted(Comparator.comparing((CharacterDetailResponse.Reading reading) -> reading.words().size())
                        .reversed())
                .toList();
        return new CharacterDetailResponse(character, readings);
    }

    /**
     * Tách lại cả kho từ cột Hán Việt. Chạy lại bao nhiêu lần cũng cho cùng kết quả; từ không tách được thì bỏ các chữ
     * thành phần cũ (nếu có) để không hiện âm sai.
     *
     * @param dryRun chỉ báo cáo, không ghi gì
     */
    @Transactional
    public CharacterRebuildReport rebuild(boolean dryRun) {
        List<Kanji> words = kanjiRepository.findAllWithCharacterParts();
        int split = 0;
        int kanaOnly = 0;
        int changed = 0;
        List<CharacterRebuildReport.SkippedWord> skipped = new ArrayList<>();
        for (Kanji word : words) {
            HanVietSplitter.Split result = HanVietSplitter.split(word.getCharacter(), word.getHanViet());
            if (result.problem() == null) {
                split++;
            } else if (result.problem() == HanVietSplitter.Problem.NO_KANJI) {
                kanaOnly++;
            } else {
                skipped.add(new CharacterRebuildReport.SkippedWord(word.getId(), word.getCharacter(),
                        word.getHanViet(), result.problem().name()));
            }
            if (!word.getCharacterParts().equals(result.parts())) {
                changed++;
                if (!dryRun) {
                    word.replaceCharacterParts(result.parts());
                }
            }
        }
        skipped.sort(Comparator.comparing(CharacterRebuildReport.SkippedWord::id));
        return new CharacterRebuildReport(dryRun, words.size(), split, kanaOnly, changed, skipped);
    }
}
