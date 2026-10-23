package com.kanjimastery.backend.service;

import com.kanjimastery.backend.dto.StreakResponse;

import java.time.LocalDate;
import java.util.Collection;
import java.util.TreeSet;

/** Tính chuỗi ngày học từ tập các ngày học có học (logic thuần, không phụ thuộc DB hay múi giờ). */
final class StudyStreaks {

    private StudyStreaks() {
    }

    static StreakResponse of(Collection<LocalDate> studyDays, LocalDate today) {
        TreeSet<LocalDate> days = new TreeSet<>(studyDays);
        boolean studiedToday = days.contains(today);
        // Hôm nay chưa học thì chuỗi vẫn còn tới hôm qua: chưa hết ngày thì chưa đứt.
        LocalDate day = studiedToday ? today : today.minusDays(1);
        int current = 0;
        while (days.contains(day)) {
            current++;
            day = day.minusDays(1);
        }

        int longest = 0;
        int run = 0;
        LocalDate previous = null;
        for (LocalDate studied : days) {
            run = previous != null && previous.plusDays(1).equals(studied) ? run + 1 : 1;
            longest = Math.max(longest, run);
            previous = studied;
        }
        return new StreakResponse(current, longest, studiedToday);
    }
}
