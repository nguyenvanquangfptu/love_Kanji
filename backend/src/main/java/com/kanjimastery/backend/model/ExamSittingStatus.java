package com.kanjimastery.backend.model;

/** Các giá trị hợp lệ của cột {@code exam_sittings.status}. */
public enum ExamSittingStatus {
    IN_PROGRESS,
    /** Đã làm xong mọi phần đã chọn. */
    COMPLETED,
    /** Bỏ dở giữa các phần quá lâu - giữ kết quả các phần đã làm. */
    ABANDONED
}
