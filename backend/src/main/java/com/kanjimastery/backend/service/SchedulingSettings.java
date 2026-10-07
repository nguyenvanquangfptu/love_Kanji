package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.SchedulerType;

/**
 * Cách xếp lịch ôn của một người học.
 *
 * @param desiredRetention tỉ lệ nhớ mong muốn khi xếp lịch bằng FSRS
 * @param fsrs             mô hình trí nhớ của người học (tham số riêng nếu đã tối ưu) - luôn được cập nhật, kể cả khi
 *                         lịch ôn đang theo SM-2
 */
public record SchedulingSettings(SchedulerType scheduler, double desiredRetention, Fsrs fsrs) {

    public static final SchedulingSettings DEFAULT = new SchedulingSettings(SchedulerType.SM2, 0.9, Fsrs.withDefaults());

    public boolean usesFsrs() {
        return scheduler == SchedulerType.FSRS;
    }
}
