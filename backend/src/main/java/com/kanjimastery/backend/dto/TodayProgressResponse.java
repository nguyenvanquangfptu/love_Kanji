package com.kanjimastery.backend.dto;

import java.util.List;

/**
 * Những gì người học đã làm trong ngày học hôm nay, cho trang "Học hôm nay".
 *
 * @param cardsAnswered   số lượt chấm thẻ ôn hôm nay
 * @param recentWords     các từ đã ôn bằng thẻ hôm nay, từ ôn gần nhất trước (tối đa vài chục) - để luyện trắc nghiệm
 * @param quizAnswers     số câu trắc nghiệm đã trả lời hôm nay
 */
public record TodayProgressResponse(long cardsAnswered, List<Long> recentWords, long quizAnswers) {
}
