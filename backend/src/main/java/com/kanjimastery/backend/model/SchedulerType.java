package com.kanjimastery.backend.model;

/** Các giá trị hợp lệ của cột {@code user_learning_profiles.scheduler}: thuật toán xếp lịch ôn. */
public enum SchedulerType {
    /** SuperMemo SM-2 - mặc định, như từ đầu. */
    SM2,
    /** FSRS-6 - khoảng ôn theo tỉ lệ nhớ mong muốn của người học. */
    FSRS
}
