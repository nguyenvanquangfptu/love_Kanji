package com.kanjimastery.backend.model;

/**
 * Các phần của đề JLPT (cột {@code user_exam_attempts.section}, {@code exam_passages.section}); chưa làm phần Nghe
 * hiểu.
 */
public enum ExamSection {
    /** 言語知識（文字・語彙）. */
    VOCABULARY("Từ vựng"),
    /** 言語知識（文法）. */
    GRAMMAR("Ngữ pháp"),
    /** 読解. */
    READING("Đọc hiểu");

    private final String vietnameseName;

    ExamSection(String vietnameseName) {
        this.vietnameseName = vietnameseName;
    }

    /**
     * Phần thuộc môn Kiến thức ngôn ngữ (言語知識, thang 0-60 của N3) - điểm ước tính và bảng xếp hạng đề JLPT tính trên
     * các phần này; Đọc hiểu là một môn tính điểm riêng.
     */
    public boolean languageKnowledge() {
        return this == VOCABULARY || this == GRAMMAR;
    }

    /** Tên phần thi trong thông báo cho người học. */
    public String vietnameseName() {
        return vietnameseName;
    }
}
