package com.kanjimastery.backend.dto;

/**
 * Đoạn văn của 問題3 文章の文法 trong bài thi: chỗ trống đánh dấu 【1】【2】..., câu hỏi điền vào chỗ trống 【n】 có
 * {@code passageId} và {@code blankNo} = n.
 */
public record ExamPassageResponse(Long id, String title, String content) {
}
