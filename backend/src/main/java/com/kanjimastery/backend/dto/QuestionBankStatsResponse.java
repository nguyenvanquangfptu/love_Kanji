package com.kanjimastery.backend.dto;

import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.model.ExamSection;
import java.util.List;

/**
 * Ngân hàng câu đề JLPT của một cấp độ: theo từng dạng câu, số câu theo trạng thái và đã đủ cho bao nhiêu đề.
 *
 * @param jlptLevel cấp độ
 * @param types     các dạng câu theo thứ tự trong đề (phần Từ vựng rồi Ngữ pháp)
 */
public record QuestionBankStatsResponse(String jlptLevel, List<TypeStats> types) {

    /**
     * @param section   phần thi ({@link com.kanjimastery.backend.model.ExamSection})
     * @param type      dạng câu ({@link com.kanjimastery.backend.model.JlptQuestionType})
     * @param perExam   số câu của dạng này trong một đề thật
     * @param exams     số đề đủ câu đã duyệt (approved / perExam)
     */
    public record TypeStats(ExamSection section, JlptQuestionType type, int perExam, long approved, long draft, long rejected,
                            long retired, long exams) {
    }
}
