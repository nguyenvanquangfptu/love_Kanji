package com.kanjimastery.backend.dto;

import com.kanjimastery.backend.model.ExamSection;

/**
 * Đoạn văn trong bài thi. Phần Ngữ pháp (問題3 文章の文法): chỗ trống đánh dấu 【1】【2】..., câu hỏi điền vào chỗ trống
 * 【n】 có {@code passageId} và {@code blankNo} = n. Phần Đọc hiểu: bài đọc, phần gạch chân đánh dấu 〔下線〕...〔/下線〕,
 * bảng viết thành các dòng | ô | ô |; {@code blankNo} của câu hỏi là thứ tự câu trong bài.
 */
public record ExamPassageResponse(Long id, ExamSection section, String title, String content) {
}
