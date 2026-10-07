package com.kanjimastery.backend.job;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Cảnh báo chia partition review_logs khi tới một trong hai ngưỡng. */
class FsrsOptimizationJobTest {

    @Test
    void needsPartitioning_shouldTriggerOnEitherRowsOrSize() {
        assertThat(FsrsOptimizationJob.needsPartitioning(3_778, 968 * 1024)).isFalse();
        assertThat(FsrsOptimizationJob.needsPartitioning(FsrsOptimizationJob.PARTITION_ROWS, 0)).isTrue();
        assertThat(FsrsOptimizationJob.needsPartitioning(0, FsrsOptimizationJob.PARTITION_BYTES)).isTrue();
    }
}
