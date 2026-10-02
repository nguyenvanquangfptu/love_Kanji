package com.kanjimastery.backend.model;

/** Các phần của đề JLPT (cột {@code user_exam_attempts.section}); chỉ làm phần Kiến thức ngôn ngữ. */
public final class ExamSection {
    /** 言語知識（文字・語彙）. */
    public static final String VOCABULARY = "VOCABULARY";
    /** 言語知識（文法）. */
    public static final String GRAMMAR = "GRAMMAR";

    private ExamSection() {
    }

    /** Tên phần thi trong thông báo cho người học. */
    public static String vietnameseName(String section) {
        return switch (section) {
            case VOCABULARY -> "Từ vựng";
            case GRAMMAR -> "Ngữ pháp";
            default -> section;
        };
    }
}
