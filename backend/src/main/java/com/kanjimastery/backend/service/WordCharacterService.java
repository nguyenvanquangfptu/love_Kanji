package com.kanjimastery.backend.service;

import com.kanjimastery.backend.dto.CharacterRebuildReport;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.repository.KanjiRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Hán Việt từng chữ của các từ vựng (bảng word_characters). Từ thêm/sửa qua trang quản trị được tách ngay trong
 * {@link KanjiService}; từ nhập thẳng vào DB bằng SQL thì cần chạy {@link #rebuild(boolean)}.
 */
@Service
@RequiredArgsConstructor
public class WordCharacterService {

    private final KanjiRepository kanjiRepository;

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
