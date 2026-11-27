package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamSection;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.KanjiRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Gắn lại câu thi từ vựng chưa gắn với từ nào: lúc nhập đề, từ được hỏi chưa có trong kho thì câu chỉ được cảnh báo, và
 * nhập lại đề cũng bỏ qua câu đã có. Khi kho có thêm từ (nhập bài mới), chạy lại cách tìm từ của lúc nhập đề
 * ({@link ExamWordMatcher}) cho các câu đó - làm sai thì từ được đưa vào ôn tập, một buổi thi không hỏi một từ hai lần.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamWordLinkService {

    /** Số từ chưa có trong kho nêu tên trong báo cáo, từ được hỏi nhiều câu nhất trước. */
    static final int MISSING_EXAMPLES = 30;
    private static final List<JlptQuestionType> VOCABULARY_TYPES = Arrays.stream(JlptQuestionType.values())
            .filter(type -> type.section() == ExamSection.VOCABULARY)
            .toList();

    private final ExamQuestionRepository questionRepository;
    private final KanjiRepository kanjiRepository;

    /**
     * @param checked      số câu từ vựng chưa gắn từ trước lượt này
     * @param linked       số câu gắn được (hoặc sẽ gắn được, khi chạy thử)
     * @param missingWords số cách viết khác nhau vẫn chưa có trong kho
     * @param missing      các cách viết đó (tối đa {@link #MISSING_EXAMPLES}) kèm số câu hỏi đến
     */
    public record Result(boolean dryRun, int checked, int linked, int missingWords, List<MissingWord> missing) {
    }

    public record MissingWord(String written, int questions) {
    }

    private record Asked(ExamQuestion question, ExamWordMatcher.Hint hint) {
    }

    /** Gắn các câu tìm được từ; {@code dryRun} chỉ đếm, không ghi. */
    @Transactional
    public Result relink(boolean dryRun) {
        List<ExamQuestion> questions = questionRepository.findWithoutWords(VOCABULARY_TYPES);
        List<Asked> asked = new ArrayList<>();
        for (ExamQuestion question : questions) {
            ExamWordMatcher.Hint hint = ExamWordMatcher.hintOf(question);
            if (hint != null) {
                asked.add(new Asked(question, hint));
            }
        }
        Set<String> keys = ExamWordMatcher.keys(asked.stream().map(Asked::hint).toList());
        Map<String, List<Kanji>> byCharacter = keys.isEmpty() ? Map.of()
                : kanjiRepository.findAllByCharacterIn(keys).stream().collect(Collectors.groupingBy(Kanji::getCharacter));

        int linked = 0;
        Map<String, Integer> missing = new LinkedHashMap<>();
        for (Asked one : asked) {
            List<Kanji> matches = ExamWordMatcher.match(one.hint(), byCharacter);
            if (matches.isEmpty()) {
                missing.merge(one.hint().written(), 1, Integer::sum);
                continue;
            }
            linked++;
            if (!dryRun) {
                matches.stream().map(Kanji::getId).filter(Objects::nonNull)
                        .forEach(one.question().getKanjiIds()::add);
            }
        }
        if (!dryRun) {
            log.info("Gắn lại từ vựng cho câu thi: {}/{} câu, còn {} từ chưa có trong kho", linked, questions.size(),
                    missing.size());
        }
        List<MissingWord> examples = missing.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(MISSING_EXAMPLES)
                .map(entry -> new MissingWord(entry.getKey(), entry.getValue()))
                .toList();
        return new Result(dryRun, questions.size(), linked, missing.size(), examples);
    }
}
