package com.kanjimastery.backend.service;

import com.kanjimastery.backend.config.SrsProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class StudyCalendarTest {

    /** Máy chủ chạy giờ UTC như trong container; người học ở Việt Nam (UTC+7), ngày học bắt đầu lúc 4 giờ sáng. */
    private static StudyCalendar calendarAt(String utcInstant) {
        return new StudyCalendar(new SrsProperties(), Clock.fixed(Instant.parse(utcInstant), ZoneOffset.UTC));
    }

    @Test
    void today_shouldStillBeYesterday_whenStudyingPastMidnightInVietnam() {
        // 20:30 UTC = 3:30 sáng 03/10 ở Việt Nam: chưa tới 4 giờ nên vẫn là ngày học 02/10.
        assertThat(calendarAt("2026-10-02T20:30:00Z").today()).isEqualTo(LocalDate.of(2026, 10, 2));
        // 21:30 UTC = 4:30 sáng 03/10: đã sang ngày học mới.
        assertThat(calendarAt("2026-10-02T21:30:00Z").today()).isEqualTo(LocalDate.of(2026, 10, 3));
    }

    @Test
    void startOfToday_shouldBeFourAmInVietnam_expressedInServerTime() {
        // Ngày học 02/10 bắt đầu 04:00 ở Việt Nam = 21:00 UTC ngày 01/10.
        assertThat(calendarAt("2026-10-02T10:00:00Z").startOfToday()).isEqualTo(LocalDateTime.of(2026, 10, 1, 21, 0));
    }
}
