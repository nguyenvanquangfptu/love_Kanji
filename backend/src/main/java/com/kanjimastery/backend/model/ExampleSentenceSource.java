package com.kanjimastery.backend.model;

/** Ai viết câu ví dụ của một từ (cột kanji.example_sentence_source, V42); null = câu có từ trước, không rõ nguồn. */
public enum ExampleSentenceSource {
    /** Soạn tay, hoặc nhập cùng bài từ vựng. */
    MANUAL,
    /** Gemini viết: câu thi sinh từ câu này phải chờ duyệt. */
    AI
}
