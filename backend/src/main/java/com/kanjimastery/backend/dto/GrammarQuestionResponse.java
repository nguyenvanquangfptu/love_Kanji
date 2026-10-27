package com.kanjimastery.backend.dto;

import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.JlptQuestionType;

import java.util.List;

/**
 * Câu hỏi dùng để ôn một điểm ngữ pháp - không kèm đáp án: server chấm, đáp án và giải thích trả về sau khi trả lời
 * ({@link GrammarReviewResponse}).
 *
 * @param sentenceRuby {@code sentence} chia đoạn kèm furigana; null nếu không có câu
 */
public record GrammarQuestionResponse(Long id, JlptQuestionType questionType, String questionText, String sentence,
                                      List<RubySegment> sentenceRuby, String highlight, String optionA,
                                      String optionB, String optionC, String optionD) {

    public static GrammarQuestionResponse from(ExamQuestion question, List<RubySegment> sentenceRuby) {
        return new GrammarQuestionResponse(question.getId(), question.getQuestionType(), question.getQuestionText(),
                question.getSentence(), sentenceRuby, question.getHighlight(), question.getOptionA(),
                question.getOptionB(), question.getOptionC(), question.getOptionD());
    }
}
