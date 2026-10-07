package com.kanjimastery.backend.model;

/** Các phần của đề JLPT (cột {@code user_exam_attempts.section}); chỉ làm phần Kiến thức ngôn ngữ. */
public enum ExamSection {
    /** 言語知識（文字・語彙）. */
    VOCABULARY("Từ vựng"),
    /** 言語知識（文法）. */
    GRAMMAR("Ngữ pháp");

    private final String vietnameseName;

    ExamSection(String vietnameseName) {
        this.vietnameseName = vietnameseName;
    }

    /** Tên phần thi trong thông báo cho người học. */
    public String vietnameseName() {
        return vietnameseName;
    }
}
