package com.kanjimastery.backend.model;

/** Các giá trị hợp lệ của cột {@code exam_questions.question_type}: dạng câu (大問) trong đề JLPT. */
public enum JlptQuestionType {
    /** 文字・語彙 問題1: chọn cách đọc của từ chữ Hán được gạch chân. */
    KANJI_READING(ExamSection.VOCABULARY),
    /** 文字・語彙 問題2: chọn cách viết bằng chữ Hán (N5: cả katakana) của từ viết bằng hiragana. */
    ORTHOGRAPHY(ExamSection.VOCABULARY),
    /** 文字・語彙 問題3 文脈規定: chọn từ hợp ngữ cảnh điền vào （　　）. */
    CONTEXT(ExamSection.VOCABULARY),
    /** 文字・語彙 問題4 言い換え類義: chọn câu/cụm gần nghĩa nhất với phần gạch chân. */
    PARAPHRASE(ExamSection.VOCABULARY),
    /** 文字・語彙 問題5 用法: chọn câu dùng từ đúng. */
    USAGE(ExamSection.VOCABULARY),
    /** 文法 問題1 文法形式の判断: chọn dạng ngữ pháp điền vào （　）. */
    GRAMMAR_FORM(ExamSection.GRAMMAR),
    /** 文法 問題2 文の組み立て: sắp xếp 4 cụm, chọn cụm ở ô ★. */
    SENTENCE_ORDER(ExamSection.GRAMMAR),
    /** 文法 問題3 文章の文法: đoạn văn có nhiều chỗ trống. */
    TEXT_GRAMMAR(ExamSection.GRAMMAR),
    /** 読解 内容理解（短文）: bài đọc ngắn. */
    READING_SHORT(ExamSection.READING),
    /** 読解 内容理解（中文）: bài đọc trung bình. */
    READING_MID(ExamSection.READING),
    /** 読解 内容理解（長文）: bài đọc dài. */
    READING_LONG(ExamSection.READING),
    /** 読解 情報検索: tìm thông tin trong thông báo, tờ rơi, bảng biểu. */
    INFO_SEARCH(ExamSection.READING);

    private final ExamSection section;

    JlptQuestionType(ExamSection section) {
        this.section = section;
    }

    /** Phần thi có dạng câu này. */
    public ExamSection section() {
        return section;
    }

    /** Câu hỏi của dạng này đi theo một đoạn văn ({@link ExamPassage}): 文章の文法 và mọi dạng Đọc hiểu. */
    public boolean usesPassage() {
        return this == TEXT_GRAMMAR || section == ExamSection.READING;
    }
}
