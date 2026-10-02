package com.kanjimastery.backend.model;

/** Các giá trị hợp lệ của cột {@code user_learning_profiles.scheduler}: thuật toán xếp lịch ôn. */
public final class SchedulerType {
    /** SuperMemo SM-2 - mặc định, như từ đầu. */
    public static final String SM2 = "SM2";
    /** FSRS-6 - khoảng ôn theo tỉ lệ nhớ mong muốn của người học. */
    public static final String FSRS = "FSRS";

    private SchedulerType() {
    }
}
