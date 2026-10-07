package com.kanjimastery.backend.dto;

import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.model.ExamSection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** Cấu trúc đề JLPT của một cấp độ và số câu hỏi hiện có - cho trang chọn đề. */
@Getter
@Builder
@AllArgsConstructor
public class JlptLevelResponse {
    private JlptLevel jlptLevel;
    /** Theo thứ tự làm bài. */
    private List<Section> sections;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class Section {
        /** {@link com.kanjimastery.backend.model.ExamSection} */
        private ExamSection name;
        /** Số câu và thời gian của đề thật. */
        private int plannedQuestions;
        private int plannedMinutes;
        /**
         * Đề ghép được lúc này: dạng nào chưa đủ câu thì đề ít câu hơn đề thật và thời gian giảm theo tỉ lệ;
         * 0 câu = chưa làm được phần này.
         */
        private int questionCount;
        private int minutes;
        private List<Mondai> mondai;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class Mondai {
        /** Số thứ tự trong đề thật (問題1, 問題2...). */
        private int number;
        /** {@link com.kanjimastery.backend.model.JlptQuestionType} */
        private JlptQuestionType type;
        private int plannedCount;
        /** Số câu đã duyệt hiện có của dạng này. */
        private int available;
    }
}
