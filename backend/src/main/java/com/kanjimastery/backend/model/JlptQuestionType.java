package com.kanjimastery.backend.model;

/** Các giá trị hợp lệ của cột {@code exam_questions.question_type}: dạng câu (大問) trong đề JLPT. */
public enum JlptQuestionType {
    /** 文字・語彙 問題1: chọn cách đọc của từ chữ Hán được gạch chân. */
    KANJI_READING,
    /** 文字・語彙 問題2: chọn cách viết bằng chữ Hán (N5: cả katakana) của từ viết bằng hiragana. */
    ORTHOGRAPHY,
    /** 文字・語彙 問題3 文脈規定: chọn từ hợp ngữ cảnh điền vào （　　）. */
    CONTEXT,
    /** 文字・語彙 問題4 言い換え類義: chọn câu/cụm gần nghĩa nhất với phần gạch chân. */
    PARAPHRASE,
    /** 文字・語彙 問題5 用法: chọn câu dùng từ đúng. */
    USAGE,
    /** 文法 問題1 文法形式の判断: chọn dạng ngữ pháp điền vào （　）. */
    GRAMMAR_FORM,
    /** 文法 問題2 文の組み立て: sắp xếp 4 cụm, chọn cụm ở ô ★. */
    SENTENCE_ORDER,
    /** 文法 問題3 文章の文法: đoạn văn có nhiều chỗ trống. */
    TEXT_GRAMMAR
}
