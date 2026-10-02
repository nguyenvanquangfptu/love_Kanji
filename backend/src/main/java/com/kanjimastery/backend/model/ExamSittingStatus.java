package com.kanjimastery.backend.model;

/** Các giá trị hợp lệ của cột {@code exam_sittings.status}. */
public final class ExamSittingStatus {
    public static final String IN_PROGRESS = "IN_PROGRESS";
    /** Đã làm xong mọi phần đã chọn. */
    public static final String COMPLETED = "COMPLETED";
    /** Bỏ dở giữa các phần quá lâu - giữ kết quả các phần đã làm. */
    public static final String ABANDONED = "ABANDONED";

    private ExamSittingStatus() {
    }
}
