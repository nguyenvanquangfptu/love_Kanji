package com.kanjimastery.backend.model;

/** Các giá trị hợp lệ của cột {@code exam_questions.source}: câu thi soạn tay, sinh từ kho từ vựng hay do AI viết nháp. */
public enum ExamQuestionSource {
    MANUAL,
    GENERATED,
    /** AI (Gemini) viết nháp, phải qua người duyệt mới vào đề. */
    AI,
    /** Nhập từ file đề tự soạn (xem ExamImportService); cột source_ref ghi câu nào của đề nào. */
    IMPORTED
}
