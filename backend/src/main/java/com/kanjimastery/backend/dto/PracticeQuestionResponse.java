package com.kanjimastery.backend.dto;

import com.kanjimastery.backend.model.JlptQuestionType;
import java.util.List;

/**
 * Một câu luyện lại điểm ngữ pháp, kèm đáp án và giải thích (luyện không tính giờ, chấm ngay trên trình duyệt).
 *
 * @param sentenceRuby {@code sentence} chia đoạn kèm furigana; null nếu không có câu
 */
public record PracticeQuestionResponse(Long id, JlptQuestionType questionType, String questionText, String sentence,
                                       List<RubySegment> sentenceRuby,
                                       String highlight, String optionA, String optionB, String optionC,
                                       String optionD, String correctOption, String explanation,
                                       List<QuestionReviewItem.Grammar> grammarPoints) {
}
