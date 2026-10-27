package com.kanjimastery.backend.dto;

import java.time.LocalDateTime;

/**
 * Kết quả một lần ôn ngữ pháp.
 *
 * @param correctOption đáp án đúng của câu hỏi; null với thẻ lật
 * @param explanation   giải thích của câu hỏi; null với thẻ lật
 */
public record GrammarReviewResponse(boolean correct, String correctOption, String explanation,
                                    LocalDateTime nextReviewAt, int intervalDays) {
}
