package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.repository.KanjiRepository;
import com.kanjimastery.backend.service.LearnerHistoryService.PastMistake;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.kanjimastery.backend.model.QuizDirection.KANJI_TO_READING;
import static com.kanjimastery.backend.model.QuizDirection.READING_TO_KANJI;

/**
 * Dựng một câu hỏi 4 đáp án cho một từ, dùng chung cho trắc nghiệm ôn tập ({@link QuizService}) và câu thi sinh từ
 * kho từ vựng ({@link ExamQuestionGenerator}): câu ví dụ theo kiểu đề JLPT (問題1 漢字読み / 問題2 表記) khi có,
 * đáp án nhiễu là chữ Hán trông gần giống hoặc cách đọc bẫy trường âm/âm ngắt/âm đục
 * ({@link QuizDistractorGenerator}) cùng những đáp án người học từng chọn sai, thiếu mới bù bằng từ khác trong
 * {@code pool}. Không bao giờ lấy một đáp án cũng "đúng" làm đáp án nhiễu.
 */
@Component
@RequiredArgsConstructor
public class QuestionBuilder {

    static final int CHOICES = 4;
    private static final int DISTRACTOR_COUNT = CHOICES - 1;
    /** Số đáp án từng chọn sai được đưa lại vào một câu (còn lại là đáp án nhiễu gần đúng). */
    private static final int MAX_PERSONAL_TRAPS = 2;

    private final KanjiRepository kanjiRepository;
    private final QuizDistractorGenerator distractorGenerator;

    /**
     * Hướng hỏi của một câu, các đáp án nhiễu gần đúng và các đáp án sai người học từng chọn cho hướng đó
     * (chưa lọc những đáp án cũng "đúng").
     */
    public record PlannedQuestion(Kanji kanji, String direction, List<String> nearMisses,
                                  List<PastMistake> pastMistakes) {
    }

    /**
     * Một câu hỏi đã dựng. {@code shownTrap}: đáp án người học từng chọn sai có mặt trong các lựa chọn;
     * {@code trapWord}: từ có thật viết như đáp án đó (câu hỏi chọn cách viết) để người học so sánh.
     */
    public record BuiltQuestion(Kanji kanji, String direction, String prompt, String sentence, List<String> choices,
                                int correctIndex, PastMistake shownTrap, Kanji trapWord) {
    }

    /** Câu hỏi theo hướng {@code direction} cho một từ, kèm đáp án nhiễu gần đúng của hướng đó. */
    public PlannedQuestion plan(Kanji kanji, String direction, List<PastMistake> pastMistakes) {
        List<String> nearMisses = switch (direction) {
            case KANJI_TO_READING -> distractorGenerator.trapReadings(kanji.getReading(), kanji.getCharacter());
            case READING_TO_KANJI -> distractorGenerator.lookAlikeSpellings(kanji.getCharacter());
            default -> List.of();
        };
        return new PlannedQuestion(kanji, direction, nearMisses, pastMistakes);
    }

    /**
     * Các dòng trong kho trùng cách viết với đáp án nhiễu dạng chữ (cách viết nhiễu, cách viết từng chọn sai) hoặc với
     * từ đang hỏi khi có đáp án từng chọn sai - một truy vấn cho cả loạt câu. Dùng để bỏ đáp án nhiễu cũng "đúng":
     * cách viết khác mà có từ thật đọc giống hệt (thay 会 cho 合 ra 会う), hay cách đọc/nghĩa của một dòng khác cùng
     * cách viết (開く: あく, ひらく).
     */
    public Map<String, List<Kanji>> existingWords(List<PlannedQuestion> plans) {
        Set<String> spellings = new HashSet<>();
        for (PlannedQuestion plan : plans) {
            if (READING_TO_KANJI.equals(plan.direction())) {
                spellings.addAll(plan.nearMisses());
                plan.pastMistakes().forEach(mistake -> spellings.add(mistake.answer()));
            } else if (!plan.pastMistakes().isEmpty()) {
                spellings.add(plan.kanji().getCharacter());
            }
        }
        if (spellings.isEmpty()) {
            return Map.of();
        }
        return kanjiRepository.findAllByCharacterIn(spellings).stream().collect(Collectors.groupingBy(Kanji::getCharacter));
    }

    /**
     * @param exampleSentence câu ví dụ của từ, null nếu chưa có
     * @param pool            các từ để bù đáp án nhiễu (cùng bài, cùng cấp độ...)
     * @param existingWords   kết quả {@link #existingWords(List)} cho loạt câu chứa {@code plan}
     */
    public BuiltQuestion build(PlannedQuestion plan, String exampleSentence, List<Kanji> pool,
                               Map<String, List<Kanji>> existingWords) {
        Kanji kanji = plan.kanji();
        String direction = plan.direction();
        String reading = kanji.getReading();
        List<Kanji> otherRows = existingWords.getOrDefault(kanji.getCharacter(), List.of()).stream()
                .filter(row -> !row.getId().equals(kanji.getId()))
                .toList();

        String correctAnswer;
        // Đáp án khác mà cũng "đúng" - không được đưa vào làm đáp án nhiễu.
        Predicate<String> alsoCorrect;
        List<String> fallback;
        switch (direction) {
            case READING_TO_KANJI -> {
                correctAnswer = kanji.getCharacter();
                alsoCorrect = spelling -> existingWords.getOrDefault(spelling, List.of()).stream()
                        .anyMatch(word -> reading.equals(word.getReading()));
                fallback = spellingFallback(kanji, pool);
            }
            case KANJI_TO_READING -> {
                correctAnswer = reading;
                alsoCorrect = answer -> otherRows.stream().anyMatch(row -> answer.equals(row.getReading()));
                fallback = readingFallback(kanji, pool);
            }
            default -> {
                correctAnswer = kanji.getMeaning();
                alsoCorrect = answer -> otherRows.stream().anyMatch(row -> answer.equals(row.getMeaning()));
                fallback = otherWords(kanji, pool).stream().map(Kanji::getMeaning).toList();
            }
        }

        // Đáp án người học từng chọn sai đứng trước cả đáp án nhiễu gần đúng: đó là chỗ họ thật sự hay nhầm.
        List<PastMistake> traps = plan.pastMistakes().stream()
                .filter(mistake -> !mistake.answer().equals(correctAnswer) && !alsoCorrect.test(mistake.answer()))
                .limit(MAX_PERSONAL_TRAPS)
                .toList();
        List<String> nearMisses = Stream.concat(traps.stream().map(PastMistake::answer), plan.nearMisses().stream())
                .filter(alsoCorrect.negate())
                .toList();

        List<String> choices = buildChoices(correctAnswer, nearMisses, fallback);
        PastMistake shownTrap = traps.stream().filter(trap -> choices.contains(trap.answer())).findFirst().orElse(null);
        // Cách viết từng chọn nhầm là một từ có thật: kèm cách đọc và nghĩa để người học so sánh.
        Kanji trapWord = shownTrap != null && READING_TO_KANJI.equals(direction)
                ? existingWords.getOrDefault(shownTrap.answer(), List.of()).stream().findFirst().orElse(null)
                : null;
        String prompt = READING_TO_KANJI.equals(direction) ? reading : kanji.getCharacter();

        return new BuiltQuestion(kanji, direction, prompt, sentenceForQuestion(exampleSentence, kanji, direction),
                choices, choices.indexOf(correctAnswer), shownTrap, trapWord);
    }

    /**
     * Kiểu đề JLPT: hỏi cách đọc thì giữ từ dạng Kanji trong câu; hỏi cách viết thì thay từ đó
     * bằng hiragana. Giao diện gạch chân {@code prompt} bên trong câu.
     */
    private static String sentenceForQuestion(String exampleSentence, Kanji kanji, String direction) {
        if (!StringUtils.hasText(exampleSentence) || !standsAlone(exampleSentence, kanji.getCharacter())) {
            return null;
        }
        if (!READING_TO_KANJI.equals(direction)) {
            return exampleSentence;
        }
        // Cách đọc dạng "み(る)" (chữ Hán đơn kèm đuôi) không thay thẳng vào câu được; cách đọc đã có sẵn trong câu
        // (は của 歯 trong 私は歯を磨く) thì không biết gạch chân chỗ nào.
        if (kanji.getReading().contains("(") || exampleSentence.contains(kanji.getReading())) {
            return null;
        }
        return exampleSentence.replaceFirst(Pattern.quote(kanji.getCharacter()), Matcher.quoteReplacement(kanji.getReading()));
    }

    /**
     * Câu ví dụ chỉ dùng được khi từ xuất hiện đúng một lần, và từ một chữ Hán không dính chữ Hán khác ở hai bên:
     * 日 trong 今日, 年 trong 今年, 十 trong 十個 là một phần của từ khác, đọc khác hẳn.
     */
    private static boolean standsAlone(String sentence, String word) {
        int at = sentence.indexOf(word);
        if (at < 0 || sentence.indexOf(word, at + word.length()) >= 0) {
            return false;
        }
        if (word.codePointCount(0, word.length()) != 1 || !QuizDistractorGenerator.isKanji(word.codePointAt(0))) {
            return true;
        }
        int end = at + word.length();
        boolean kanjiBefore = at > 0 && QuizDistractorGenerator.isKanji(sentence.codePointBefore(at));
        boolean kanjiAfter = end < sentence.length() && QuizDistractorGenerator.isKanji(sentence.codePointAt(end));
        return !kanjiBefore && !kanjiAfter;
    }

    /** Ưu tiên đáp án nhiễu gần đúng, thiếu mới bù bằng {@code fallback}. */
    private static List<String> buildChoices(String correctAnswer, List<String> nearMisses, List<String> fallback) {
        Set<String> distractors = new LinkedHashSet<>();
        for (List<String> source : List.of(nearMisses, fallback)) {
            for (String value : source) {
                if (distractors.size() >= DISTRACTOR_COUNT) break;
                if (StringUtils.hasText(value) && !value.equals(correctAnswer)) {
                    distractors.add(value);
                }
            }
        }

        List<String> choices = new ArrayList<>(distractors);
        choices.add(correctAnswer);
        Collections.shuffle(choices);
        return choices;
    }

    /**
     * Bù cho câu hỏi chọn cách viết: từ khác trong bài có chữ Hán, không đọc giống đáp án (以外 cho いがい của 意外
     * cũng "đúng"). Ưu tiên từ cùng độ dài có chung một chữ Hán hoặc cùng đuôi okurigana (厚い với 高い, không phải 交番).
     */
    private static List<String> spellingFallback(Kanji kanji, List<Kanji> pool) {
        String word = kanji.getCharacter();
        Set<Integer> wordKanji = word.codePoints().filter(QuizDistractorGenerator::isKanji).boxed().collect(Collectors.toSet());
        int lastChar = word.codePointBefore(word.length());
        boolean kanaEnding = !QuizDistractorGenerator.isKanji(lastChar);
        return rankBySimilarity(otherWords(kanji, pool).stream()
                        .filter(other -> other.getCharacter().codePoints().anyMatch(QuizDistractorGenerator::isKanji))
                        .filter(other -> !kanji.getReading().equals(other.getReading()))
                        .map(Kanji::getCharacter)
                        .toList(),
                word,
                other -> other.codePoints().anyMatch(wordKanji::contains)
                        || kanaEnding && other.codePointBefore(other.length()) == lastChar);
    }

    /** Bù cho câu hỏi chọn cách đọc: ưu tiên cách đọc cùng độ dài và cùng đuôi (動詞 〜める với 〜める). */
    private static List<String> readingFallback(Kanji kanji, List<Kanji> pool) {
        String reading = kanji.getReading();
        char ending = reading.charAt(reading.length() - 1);
        return rankBySimilarity(otherWords(kanji, pool).stream()
                        .map(Kanji::getReading)
                        .filter(StringUtils::hasText)
                        .toList(),
                reading,
                other -> other.charAt(other.length() - 1) == ending);
    }

    /** Xếp hạng giữ ngẫu nhiên trong từng nhóm: cùng độ dài + giống nhau, cùng độ dài, giống nhau, còn lại. */
    private static List<String> rankBySimilarity(List<String> candidates, String target, Predicate<String> alike) {
        List<String> ranked = new ArrayList<>(candidates);
        Collections.shuffle(ranked);
        ranked.sort(Comparator.comparingInt(candidate ->
                (candidate.length() == target.length() ? 0 : 2) + (alike.test(candidate) ? 0 : 1)));
        return ranked;
    }

    /**
     * Các từ khác trong bài để lấy đáp án nhiễu. Bỏ cả dòng viết giống hệt (cùng từ, nghĩa khác như つく "sáng [điện]"
     * và つく "dính"): hỏi nghĩa hay cách đọc của chữ đó thì nghĩa/cách đọc của dòng kia cũng "đúng".
     */
    private static List<Kanji> otherWords(Kanji kanji, List<Kanji> pool) {
        List<Kanji> others = pool.stream()
                .filter(k -> !k.getId().equals(kanji.getId()) && !k.getCharacter().equals(kanji.getCharacter()))
                .collect(Collectors.toList());
        Collections.shuffle(others);
        return others;
    }
}
