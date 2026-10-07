package com.kanjimastery.backend.dto;

import com.kanjimastery.backend.model.JlptQuestionType;
import java.util.List;

/** Một câu luyện lại điểm ngữ pháp, kèm đáp án và giải thích (luyện không tính giờ, chấm ngay trên trình duyệt). */
public record PracticeQuestionResponse(Long id, JlptQuestionType questionType, String questionText, String sentence,
                                       String highlight, String optionA, String optionB, String optionC,
                                       String optionD, String correctOption, String explanation,
                                       List<QuestionReviewItem.Grammar> grammarPoints) {
}
