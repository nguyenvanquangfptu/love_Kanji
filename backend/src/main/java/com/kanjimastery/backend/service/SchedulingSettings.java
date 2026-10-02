package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.SchedulerType;

/**
 * Cách xếp lịch ôn của một người học.
 *
 * @param scheduler        {@link SchedulerType}
 * @param desiredRetention tỉ lệ nhớ mong muốn khi xếp lịch bằng FSRS
 */
public record SchedulingSettings(String scheduler, double desiredRetention) {

    public static final SchedulingSettings DEFAULT = new SchedulingSettings(SchedulerType.SM2, 0.9);

    public boolean usesFsrs() {
        return SchedulerType.FSRS.equals(scheduler);
    }
}
