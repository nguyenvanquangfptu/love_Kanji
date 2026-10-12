package com.kanjimastery.backend.dto;

/**
 * Một điểm ngữ pháp người học hay làm sai trong các đề JLPT gần đây.
 *
 * @param connection    cách nối; null nếu chưa có
 * @param explanationVi giải thích và ví dụ; null nếu chưa có
 * @param wrong         số câu làm sai
 * @param answered      số câu đã trả lời (không tính câu bỏ trống)
 */
public record WeakGrammarResponse(Long id, String pattern, String meaningVi, String connection, String explanationVi,
                                  int wrong, int answered) {
}
