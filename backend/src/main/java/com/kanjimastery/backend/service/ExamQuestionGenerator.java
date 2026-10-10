package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.QuizDirection;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionSource;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.KanjiRepository;
import com.kanjimastery.backend.service.QuestionBuilder.BuiltQuestion;
import com.kanjimastery.backend.service.QuestionBuilder.PlannedQuestion;
import com.kanjimastery.backend.service.WordClassifier.PartOfSpeech;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.kanjimastery.backend.model.QuizDirection.KANJI_TO_READING;
import static com.kanjimastery.backend.model.QuizDirection.MEANING;
import static com.kanjimastery.backend.model.QuizDirection.READING_TO_KANJI;

/**
 * Sinh câu thi từ kho từ vựng, cùng cách dựng câu hỏi với trắc nghiệm ({@link QuestionBuilder}). Mỗi từ trong các bài
 * của một cấp độ (tag "N4-01", "N4-02"...) có câu ví dụ chứa từ (đứng riêng, không nằm trong từ khác) thì được các câu
 * theo kiểu đề JLPT: đọc chữ Hán gạch chân (問題1 漢字読み) và viết bằng chữ Hán (問題2 表記) khi từ có chữ Hán và cách
 * đọc, viết bằng katakana (問題2 表記 của N5) với từ katakana, chọn từ hợp ngữ cảnh (問題3 文脈規定) với danh từ, động từ
 * khi đủ 3 từ khác cùng từ loại làm đáp án nhiễu. Từ nào có nghĩa cũng được một câu hỏi nghĩa (đáp án tiếng Việt, chỉ
 * dùng cho thi nhanh). Đáp án nhiễu lấy từ các từ khác cùng cấp độ. Mỗi câu
 * gắn với từ của nó; chạy lại chỉ sinh câu cho cặp (từ, dạng câu) chưa có.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExamQuestionGenerator {

    /** Số câu dựng mỗi lượt - một truy vấn tìm từ trùng cách viết cho cả lượt. */
    private static final int BATCH_SIZE = 300;
    /** Cột option_a..option_d là VARCHAR(255). */
    private static final int MAX_OPTION_LENGTH = 255;
    static final String BLANK = "（　　）";
    /** Câu điền từ cần đủ ngữ cảnh: phần còn lại của câu ngắn quá (「空が（　　）。」) thì nhiều từ cùng hợp. */
    static final int MIN_CONTEXT_LENGTH = 8;
    /**
     * Chỉ hỏi điền từ với danh từ, động từ: câu với tính từ, phó từ thường hợp với nhiều đáp án (「（　　）猫がいます」:
     * 白い, 汚い đều được), máy không phân biệt được.
     */
    private static final Set<PartOfSpeech> CONTEXT_CLASSES = EnumSet.of(PartOfSpeech.NOUN, PartOfSpeech.VERB);
    /** Đề có câu 表記 viết bằng katakana: chỉ N5. */
    private static final Set<JlptLevel> KATAKANA_ORTHOGRAPHY_LEVELS = EnumSet.of(JlptLevel.N5);
    static final String KATAKANA_ORTHOGRAPHY_TEXT = "Chọn cách viết bằng katakana của từ được gạch chân.";

    private final KanjiRepository kanjiRepository;
    private final ExamQuestionRepository questionRepository;
    private final QuestionBuilder questionBuilder;

    /** @param words số từ trong các bài của cấp độ; {@code created} số câu thi mới */
    public record Result(JlptLevel level, int words, int created) {
    }

    @Transactional
    public Result generate(String level) {
        JlptLevel normalized = Levels.require(level);
        List<Kanji> pool = kanjiRepository.findAllByTagNamePrefix(normalized + "-%");
        Set<String> existing = questionRepository.generatedQuestionWords(normalized.name()).stream()
                .map(row -> row.getKind() + ":" + row.getKanjiId())
                .collect(Collectors.toSet());
        List<PlannedQuestion> plans = new ArrayList<>();
        List<Kanji> contextWords = new ArrayList<>();
        List<Kanji> katakanaWords = new ArrayList<>();
        boolean asksKatakana = KATAKANA_ORTHOGRAPHY_LEVELS.contains(normalized);
        for (Kanji word : pool) {
            for (QuizDirection skill : skillsFor(word)) {
                if (!existing.contains(kind(skill) + ":" + word.getId())) {
                    plans.add(questionBuilder.plan(word, skill, List.of()));
                }
            }
            if (inUsableSentence(word) && !existing.contains(JlptQuestionType.CONTEXT + ":" + word.getId())) {
                contextWords.add(word);
            }
            if (asksKatakana && inKatakanaSentence(word)
                    && !existing.contains(JlptQuestionType.ORTHOGRAPHY + ":" + word.getId())) {
                katakanaWords.add(word);
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
        created += questionRepository.saveAll(contextQuestions(normalized, contextWords, pool)).size();
        created += questionRepository.saveAll(katakanaQuestions(normalized, katakanaWords)).size();
        if (created > 0) {
            log.info("Đã sinh {} câu thi {} từ {} từ vựng.", created, normalized, pool.size());
        }
        return new Result(normalized, pool.size(), created);
    }

    /** Khoá chống sinh trùng: dạng câu JLPT nếu có, không thì kỹ năng (câu hỏi nghĩa). */
    private static String kind(QuizDirection skill) {
        return switch (skill) {
            case KANJI_TO_READING -> JlptQuestionType.KANJI_READING.name();
            case READING_TO_KANJI -> JlptQuestionType.ORTHOGRAPHY.name();
            case MEANING, TYPE_READING -> skill.name();
        };
    }

    /** Đọc và viết cần chữ Hán, cách đọc và một câu ví dụ chứa từ; nghĩa thì từ nào cũng hỏi được. */
    private static List<QuizDirection> skillsFor(Kanji word) {
        List<QuizDirection> skills = new ArrayList<>();
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

    private static boolean inUsableSentence(Kanji word) {
        return StringUtils.hasText(word.getExampleSentence())
                && QuestionBuilder.standsAlone(word.getExampleSentence(), word.getCharacter());
    }

    /** Từ katakana có câu ví dụ chứa nó, đứng riêng. */
    private static boolean inKatakanaSentence(Kanji word) {
        return KatakanaSpelling.isKatakanaWord(word.getCharacter()) && StringUtils.hasText(word.getExampleSentence())
                && KatakanaSpelling.standsAlone(word.getExampleSentence(), word.getCharacter());
    }

    /** Bỏ câu không đủ 4 đáp án (cấp độ quá ít từ) hoặc có đáp án quá dài cho cột. */
    private static Optional<ExamQuestion> toExamQuestion(JlptLevel level, BuiltQuestion question) {
        List<String> choices = question.choices();
        if (choices.size() != QuestionBuilder.CHOICES
                || choices.stream().anyMatch(choice -> choice.length() > MAX_OPTION_LENGTH)
                || !MEANING.equals(question.direction()) && question.sentence() == null) {
            return Optional.empty();
        }
        Kanji word = question.kanji();
        String reading = readingInBrackets(word);
        String questionText = switch (question.direction()) {
            case KANJI_TO_READING -> "Chọn cách đọc đúng của từ được gạch chân.";
            case READING_TO_KANJI -> "Chọn cách viết bằng chữ Hán của từ được gạch chân.";
            default -> "Từ 「" + word.getCharacter() + "」" + reading + " có nghĩa là gì?";
        };
        JlptQuestionType questionType = switch (question.direction()) {
            case KANJI_TO_READING -> JlptQuestionType.KANJI_READING;
            case READING_TO_KANJI -> JlptQuestionType.ORTHOGRAPHY;
            default -> null;
        };
        return Optional.of(question(level, word, questionText, choices, question.correctIndex())
                .sentence(question.sentence())
                .highlight(question.sentence() == null ? null : question.prompt())
                .skill(question.direction())
                .questionType(questionType)
                .build());
    }

    /**
     * 文脈規定: từ trong câu ví dụ được khoét thành {@value #BLANK}, chọn từ hợp ngữ cảnh. Đáp án nhiễu là 3 từ khác
     * cùng từ loại, cùng cấp độ, độ dài gần nhau - câu "家を（　　）" thì các lựa chọn đều là động từ, chỉ một từ hợp
     * nghĩa. Từ không xác định được từ loại hoặc không đủ 3 từ cùng loại thì bỏ qua.
     */
    private List<ExamQuestion> contextQuestions(JlptLevel level, List<Kanji> words, List<Kanji> pool) {
        if (words.isEmpty()) {
            return List.of();
        }
        // Nạp từ điển Kuromoji cho lượt này rồi bỏ - không giữ trong bộ nhớ suốt đời ứng dụng.
        WordClassifier classifier = new WordClassifier();
        Map<Long, PartOfSpeech> classes = new HashMap<>();
        for (Kanji word : pool) {
            classes.put(word.getId(), classifier.classify(word.getCharacter()));
        }
        Map<PartOfSpeech, List<Kanji>> byClass = pool.stream()
                .collect(Collectors.groupingBy(word -> classes.get(word.getId())));

        List<ExamQuestion> questions = new ArrayList<>();
        for (Kanji word : words) {
            PartOfSpeech partOfSpeech = classes.get(word.getId());
            if (!CONTEXT_CLASSES.contains(partOfSpeech)
                    || word.getExampleSentence().length() - word.getCharacter().length() < MIN_CONTEXT_LENGTH) {
                continue;
            }
            List<String> distractors = contextDistractors(word, byClass.getOrDefault(partOfSpeech, List.of()));
            if (distractors.size() < QuestionBuilder.CHOICES - 1) {
                continue;
            }
            List<String> choices = new ArrayList<>(distractors);
            choices.add(word.getCharacter());
            Collections.shuffle(choices);
            String sentence = word.getExampleSentence().replaceFirst(Pattern.quote(word.getCharacter()),
                    Matcher.quoteReplacement(BLANK));
            questions.add(question(level, word, BLANK + "に 入る ことばを えらんで ください。", choices,
                    choices.indexOf(word.getCharacter()))
                    .sentence(sentence)
                    .skill(MEANING)
                    .questionType(JlptQuestionType.CONTEXT)
                    .build());
        }
        return questions;
    }

    /**
     * 表記 katakana: từ katakana trong câu ví dụ được viết bằng hiragana (giữ ー) và gạch chân, chọn cách viết katakana
     * đúng; đáp án nhiễu là 3 cách viết sai dễ nhầm ({@link KatakanaSpelling}). Từ không đủ 3 cách viết sai thì bỏ qua.
     */
    private static List<ExamQuestion> katakanaQuestions(JlptLevel level, List<Kanji> words) {
        List<ExamQuestion> questions = new ArrayList<>();
        for (Kanji word : words) {
            String katakana = word.getCharacter();
            List<String> choices = new ArrayList<>(KatakanaSpelling.misspellings(katakana, QuestionBuilder.CHOICES - 1));
            if (choices.size() < QuestionBuilder.CHOICES - 1) {
                continue;
            }
            choices.add(katakana);
            Collections.shuffle(choices);
            String hiragana = KatakanaSpelling.toHiragana(katakana);
            questions.add(question(level, word, KATAKANA_ORTHOGRAPHY_TEXT, choices, choices.indexOf(katakana))
                    .sentence(word.getExampleSentence().replace(katakana, hiragana))
                    .highlight(hiragana)
                    .skill(READING_TO_KANJI)
                    .questionType(JlptQuestionType.ORTHOGRAPHY)
                    .build());
        }
        return questions;
    }

    /**
     * Đáp án nhiễu cho 文脈規定: cùng từ loại; khác cách viết, cách đọc với từ đang hỏi và không gần nghĩa (từ đồng
     * nghĩa trong kho cũng "đúng"); không có sẵn trong câu; ưu tiên từ dài gần bằng, ngẫu nhiên trong từng nhóm.
     */
    private static List<String> contextDistractors(Kanji word, List<Kanji> sameClass) {
        String sentence = word.getExampleSentence();
        List<Kanji> candidates = sameClass.stream()
                .filter(other -> !other.getId().equals(word.getId()))
                .filter(other -> !other.getCharacter().equals(word.getCharacter()))
                .filter(other -> other.getReading() == null || !other.getReading().equals(word.getReading()))
                .filter(other -> !Objects.equals(other.getMeaning(), word.getMeaning()))
                .filter(other -> !sharesMeaning(other.getMeaning(), word.getMeaning()))
                .filter(other -> !sentence.contains(other.getCharacter()))
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(candidates);
        int length = word.getCharacter().length();
        candidates.sort(Comparator.comparingInt(other -> Math.min(Math.abs(other.getCharacter().length() - length), 2)));
        Set<String> distractors = new LinkedHashSet<>();
        for (Kanji candidate : candidates) {
            if (distractors.size() >= QuestionBuilder.CHOICES - 1) {
                break;
            }
            distractors.add(candidate.getCharacter());
        }
        return new ArrayList<>(distractors);
    }

    /**
     * Hai nghĩa tiếng Việt có chung một cụm nghĩa (「Nhỏ, bé」 và 「Nhỏ, chi tiết」): gần nghĩa. Bỏ qua phần ghi chú trong
     * ngoặc như 「(tha động từ)」.
     */
    static boolean sharesMeaning(String meaning, String other) {
        Set<String> phrases = meaningPhrases(meaning);
        return meaningPhrases(other).stream().anyMatch(phrases::contains);
    }

    private static Set<String> meaningPhrases(String meaning) {
        if (meaning == null) {
            return Set.of();
        }
        return Arrays.stream(meaning.replaceAll("\\([^)]*\\)|（[^）]*）", "").toLowerCase(Locale.ROOT).split("[,;/、]"))
                .map(String::strip)
                .filter(phrase -> phrase.length() >= 2)
                .collect(Collectors.toSet());
    }

    private static ExamQuestion.ExamQuestionBuilder question(JlptLevel level, Kanji word, String questionText,
                                                             List<String> choices, int correctIndex) {
        return ExamQuestion.builder()
                .jlptLevel(level)
                .questionText(questionText)
                .optionA(choices.get(0))
                .optionB(choices.get(1))
                .optionC(choices.get(2))
                .optionD(choices.get(3))
                .correctOption(String.valueOf((char) ('A' + correctIndex)))
                .explanation(word.getCharacter() + readingInBrackets(word) + ": " + word.getMeaning())
                .source(ExamQuestionSource.GENERATED)
                .kanjiIds(Set.of(word.getId()));
    }

    private static String readingInBrackets(Kanji word) {
        boolean showsReading = StringUtils.hasText(word.getReading()) && !word.getReading().equals(word.getCharacter());
        return showsReading ? " (" + word.getReading() + ")" : "";
    }
}
