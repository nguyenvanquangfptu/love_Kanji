package com.kanjimastery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** Một 問題 của phần đề JLPT đang làm. */
@Getter
@Builder
@AllArgsConstructor
public class ExamMondaiResponse {
    /** Số thứ tự trong đề thật (問題1, 問題2...). */
    private int number;
    /** {@link com.kanjimastery.backend.model.JlptQuestionType} */
    private String type;
    /** Số câu của 問題 này trong bài. */
    private int questionCount;
    /** Số câu của dạng này trong đề thật - lớn hơn questionCount khi ngân hàng câu hỏi chưa đủ. */
    private int plannedCount;
}
