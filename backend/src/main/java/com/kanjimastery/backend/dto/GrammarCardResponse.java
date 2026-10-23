package com.kanjimastery.backend.dto;

import java.util.List;

/**
 * Một thẻ ngữ pháp trong phiên ôn hôm nay.
 *
 * @param intervals số ngày tới lần ôn sau nếu chấm Quên, Khó, Nhớ, Dễ
 * @param question  câu hỏi để ôn điểm này; null nếu điểm chưa có câu đã duyệt - khi đó ôn bằng thẻ lật
 */
public record GrammarCardResponse(Long grammarPointId, String jlptLevel, String lesson, String pattern,
                                  String connection, String meaningVi, String explanationVi, boolean newCard,
                                  List<Integer> intervals, GrammarQuestionResponse question) {
}
