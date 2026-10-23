package com.kanjimastery.backend.service;

import com.kanjimastery.backend.dto.StreakResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StudyStreaksTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);

    @Test
    void of_shouldCountBackFromToday_whenStudiedToday() {
        assertThat(StudyStreaks.of(days(0, 1, 2, 5), TODAY)).isEqualTo(new StreakResponse(3, 3, true));
    }

    @Test
    void of_shouldKeepTheStreakUntilTheDayIsOver_whenNotStudiedYetToday() {
        assertThat(StudyStreaks.of(days(1, 2), TODAY)).isEqualTo(new StreakResponse(2, 2, false));
    }

    @Test
    void of_shouldBreakTheStreak_afterAWholeDayWithoutStudy_butKeepTheLongest() {
        assertThat(StudyStreaks.of(days(2, 3, 4, 5, 9), TODAY)).isEqualTo(new StreakResponse(0, 4, false));
        assertThat(StudyStreaks.of(List.of(), TODAY)).isEqualTo(new StreakResponse(0, 0, false));
    }

    /** Các ngày cách hôm nay chừng ấy ngày. */
    private static List<LocalDate> days(int... daysAgo) {
        return Arrays.stream(daysAgo).mapToObj(TODAY::minusDays).toList();
    }
}
