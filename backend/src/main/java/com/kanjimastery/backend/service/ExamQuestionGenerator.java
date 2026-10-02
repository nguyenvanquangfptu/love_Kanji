package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionSource;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.KanjiRepository;
import com.kanjimastery.backend.service.QuestionBuilder.BuiltQuestion;
import com.kanjimastery.backend.service.QuestionBuilder.PlannedQuestion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.kanjimastery.backend.model.QuizDirection.KANJI_TO_READING;
import static com.kanjimastery.backend.model.QuizDirection.MEANING;
import static com.kanjimastery.backend.model.QuizDirection.READING_TO_KANJI;

/**
 * Sinh câu thi từ kho từ vựng, cùng cách dựng câu hỏi với trắc nghiệm ({@link QuestionBuilder}). Mỗi từ trong các bài
 * của một cấp độ (tag "N4-01", "N4-02"...): có chữ Hán, cách đọc và câu ví dụ chứa từ thì được hai câu theo kiểu đề
 * JLPT - đọc chữ Hán gạch chân (問題1 漢字読み) và viết bằng chữ Hán (問題2 表記) - và từ nào có nghĩa cũng được một câu
 * hỏi nghĩa. Đáp án nhiễu bù bằng các từ khác cùng cấp độ. Mỗi câu gắn với từ của nó; chạy lại chỉ sinh câu cho cặp
 * (từ, kỹ năng) chưa có.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExamQuestionGenerator {

    /** Số câu dựng mỗi lượt - một truy vấn tìm từ trùng cách viết cho cả lượt. */
    private static final int BATCH_SIZE = 300;
    /** Cột option_a..option_d là VARCHAR(255). */
    private static final int MAX_OPTION_LENGTH = 255;

    private final KanjiRepository kanjiRepository;
    private final ExamQuestionRepository questionRepository;
    private final QuestionBuilder questionBuilder;

    /** @param words số từ trong các bài của cấp độ; {@code created} số câu thi mới */
    public record Result(String level, int words, int created) {
    }

    @Transactional
    public Result generate(String level) {
        String normalized = level.toUpperCase();
        List<Kanji> pool = kanjiRepository.findAllByTagNamePrefix(normalized + "-%");
        Set<String> existing = questionRepository.generatedQuestionWords(normalized).stream()
                .map(row -> row.getSkill() + ":" + row.getKanjiId())
                .collect(Collectors.toSet());
        List<PlannedQuestion> plans = new ArrayList<>();
        for (Kanji word : pool) {
            for (String skill : skillsFor(word)) {
                if (!existing.contains(skill + ":" + word.getId())) {
                    plans.add(questionBuilder.plan(word, skill, List.of()));
                }
            }
        }

        int created = 0;
        for (int from = 0; from < plans.size(); from += BATCH_SIZE) {
            List<PlannedQuestion> batch = plans.subList(from, Math.min(from + BATCH_SIZE, plans.size()));
            Map<String, List<Kanji>> existingWords = questionBuilder.existingWords(batch);
            List<ExamQuestion> questions = new ArrayList<>();
            for (PlannedQuestion plan : batch) {
                BuiltQuestion question = questionBuilder.build(plan, plan.kanji().getExampleSentence(), pool, existingWords);
                toExamQuestion(normalized, question).ifPresent(questions::add);
            }
            created += questionRepository.saveAll(questions).size();
        }
        if (created > 0) {
            log.info("Đã sinh {} câu thi {} từ {} từ vựng.", created, normalized, pool.size());
        }
        return new Result(normalized, pool.size(), created);
    }

    /** Đọc và viết cần chữ Hán, cách đọc và một câu ví dụ chứa từ; nghĩa thì từ nào cũng hỏi được. */
    private static List<String> skillsFor(Kanji word) {
        List<String> skills = new ArrayList<>();
        boolean hasKanji = word.getCharacter().codePoints().anyMatch(QuizDistractorGenerator::isKanji);
        if (hasKanji && StringUtils.hasText(word.getReading()) && StringUtils.hasText(word.getExampleSentence())
                && word.getExampleSentence().contains(word.getCharacter())) {
            skills.add(KANJI_TO_READING);
            // Cách đọc dạng "み(る)" không thay vào câu được nên không hỏi viết.
            if (!word.getReading().contains("(")) {
                skills.add(READING_TO_KANJI);
            }
        }
        if (StringUtils.hasText(word.getMeaning())) {
            skills.add(MEANING);
        }
        return skills;
    }

    /** Bỏ câu không đủ 4 đáp án (cấp độ quá ít từ) hoặc có đáp án quá dài cho cột. */
    private static Optional<ExamQuestion> toExamQuestion(String level, BuiltQuestion question) {
        List<String> choices = question.choices();
        if (choices.size() != QuestionBuilder.CHOICES
                || choices.stream().anyMatch(choice -> choice.length() > MAX_OPTION_LENGTH)
                || !MEANING.equals(question.direction()) && question.sentence() == null) {
            return Optional.empty();
        }
        Kanji word = question.kanji();
        boolean showsReading = StringUtils.hasText(word.getReading()) && !word.getReading().equals(word.getCharacter());
        String reading = showsReading ? " (" + word.getReading() + ")" : "";
        String questionText = switch (question.direction()) {
            case KANJI_TO_READING -> "Chọn cách đọc đúng của từ được gạch chân.";
            case READING_TO_KANJI -> "Chọn cách viết bằng chữ Hán của từ được gạch chân.";
            default -> "Từ 「" + word.getCharacter() + "」" + reading + " có nghĩa là gì?";
        };
        return Optional.of(ExamQuestion.builder()
                .jlptLevel(level)
                .questionText(questionText)
                .sentence(question.sentence())
                .highlight(question.sentence() == null ? null : question.prompt())
                .optionA(choices.get(0))
                .optionB(choices.get(1))
                .optionC(choices.get(2))
                .optionD(choices.get(3))
                .correctOption(String.valueOf((char) ('A' + question.correctIndex())))
                .explanation(word.getCharacter() + reading + ": " + word.getMeaning())
                .skill(question.direction())
                .source(ExamQuestionSource.GENERATED)
                .kanjiIds(Set.of(word.getId()))
                .build());
    }
}
