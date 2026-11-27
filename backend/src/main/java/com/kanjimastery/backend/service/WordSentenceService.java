package com.kanjimastery.backend.service;

import com.kanjimastery.backend.dto.WordSentenceResponse;
import com.kanjimastery.backend.exception.ResourceNotFoundException;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.KanjiRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Câu dùng từ đang học, để người học thấy từ trong ngữ cảnh ở mặt sau thẻ ôn: câu ví dụ của từ trước, rồi câu trong
 * ngân hàng đề có gắn từ đó (câu điền chỗ trống được điền sẵn đáp án đúng thành câu đầy đủ).
 */
@Service
@RequiredArgsConstructor
public class WordSentenceService {

    static final int MAX_SENTENCES = 3;
    /** Dạng câu có câu tiếng Nhật dùng được, theo thứ tự ưu tiên: câu có chữ Hán của từ trước. */
    private static final List<JlptQuestionType> TYPES = List.of(JlptQuestionType.KANJI_READING,
            JlptQuestionType.CONTEXT, JlptQuestionType.USAGE, JlptQuestionType.PARAPHRASE,
            JlptQuestionType.ORTHOGRAPHY);
    /** Ô trống của câu điền từ: （　　）. */
    private static final Pattern BLANK = Pattern.compile("（[\\s　]*）");
    /** Bỏ khi so câu trùng: 今月から給料が… và 今月から、給料が… là một câu. */
    private static final Pattern PUNCTUATION = Pattern.compile("[\\s　、。，．,.!?！？「」]");

    private final KanjiRepository kanjiRepository;
    private final ExamQuestionRepository questionRepository;
    private final FuriganaService furiganaService;

    @Transactional(readOnly = true)
    public List<WordSentenceResponse> sentences(Long kanjiId) {
        Kanji word = kanjiRepository.findById(kanjiId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Kanji với id: " + kanjiId));
        List<WordSentenceResponse> sentences = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        add(sentences, seen, word, word.getExampleSentence(), null, WordSentenceResponse.EXAMPLE);
        if (sentences.size() < MAX_SENTENCES) {
            List<ExamQuestion> questions = new ArrayList<>(questionRepository.findApprovedByWord(kanjiId,
                    TYPES.stream().map(Enum::name).toList(), MAX_SENTENCES * 3));
            questions.sort(Comparator.comparingInt(question -> TYPES.indexOf(question.getQuestionType())));
            for (ExamQuestion question : questions) {
                if (sentences.size() >= MAX_SENTENCES) {
                    break;
                }
                Filled filled = fill(question);
                if (filled != null) {
                    add(sentences, seen, word, filled.sentence(), filled.target(), WordSentenceResponse.EXAM);
                }
            }
        }
        return sentences;
    }

    private void add(List<WordSentenceResponse> sentences, Set<String> seen, Kanji word, String sentence,
                     String fallbackTarget, String source) {
        if (!StringUtils.hasText(sentence) || !seen.add(PUNCTUATION.matcher(sentence).replaceAll(""))) {
            return;
        }
        String text = sentence.strip();
        String written = ReadingMatcher.writtenForm(word.getCharacter(), word.getReading());
        String target = furiganaService.locate(text, written).map(span -> span.core(text))
                .orElse(fallbackTarget != null && text.contains(fallbackTarget) ? fallbackTarget : null);
        // Cách đọc trong kho chỉ đúng cho chính từ đó (怒る), không cho dạng đã chia (怒っ): để Kuromoji đoán.
        String reading = target != null && (target.equals(written) || target.equals(word.getCharacter()))
                ? word.getReading() : null;
        sentences.add(new WordSentenceResponse(text, furiganaService.annotate(text, target, reading, false), source));
    }

    /** Câu đầy đủ của một câu thi và phần được hỏi trong đó; null nếu câu không có câu tiếng Nhật dùng được. */
    record Filled(String sentence, String target) {
    }

    static Filled fill(ExamQuestion question) {
        String correct = question.getCorrectOption() == null ? null : question.optionText(question.getCorrectOption());
        String sentence = question.getSentence();
        String highlight = question.getHighlight();
        return switch (question.getQuestionType()) {
            case KANJI_READING, PARAPHRASE -> StringUtils.hasText(sentence) ? new Filled(sentence, highlight) : null;
            case ORTHOGRAPHY -> StringUtils.hasText(sentence) && StringUtils.hasText(highlight)
                    && StringUtils.hasText(correct) && sentence.contains(highlight)
                    ? new Filled(sentence.replace(highlight, correct), correct) : null;
            case CONTEXT -> StringUtils.hasText(sentence) && StringUtils.hasText(correct)
                    && BLANK.matcher(sentence).find()
                    ? new Filled(BLANK.matcher(sentence).replaceFirst(Matcher.quoteReplacement(correct.strip())),
                    correct.strip()) : null;
            case USAGE -> StringUtils.hasText(correct) ? new Filled(correct, null) : null;
            default -> null;
        };
    }
}
