package com.kanjimastery.backend.service;

import com.kanjimastery.backend.config.SrsProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * "Ngày học" của người học: tính theo {@code app.srs.day-zone} và bắt đầu lúc {@code app.srs.day-start-hour} giờ, không
 * theo giờ máy chủ (container chạy UTC, nên nửa đêm UTC là 7 giờ sáng ở Việt Nam). Các mốc thời gian trả về đã quy về
 * giờ của JVM, giống {@link LocalDateTime#now()} mà code ở chỗ khác dùng để ghi và so các cột thời gian.
 */
@Component
public class StudyCalendar {

    private final Clock clock;
    private final ZoneId dayZone;
    private final int dayStartHour;

    public StudyCalendar(SrsProperties properties, Clock clock) {
        this.clock = clock;
        this.dayZone = ZoneId.of(properties.getDayZone());
        this.dayStartHour = properties.getDayStartHour();
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /** Ngày học hiện tại (trước {@code dayStartHour} giờ sáng vẫn là ngày hôm trước). */
    public LocalDate today() {
        return ZonedDateTime.now(clock.withZone(dayZone)).minusHours(dayStartHour).toLocalDate();
    }

    /** Ngày học chứa một thời điểm (theo giờ JVM). */
    public LocalDate dayOf(LocalDateTime time) {
        return time.atZone(clock.getZone()).withZoneSameInstant(dayZone).minusHours(dayStartHour).toLocalDate();
    }

    /** Thời điểm bắt đầu ngày học hiện tại, theo giờ JVM. */
    public LocalDateTime startOfToday() {
        return startOf(today());
    }

    /** Thời điểm bắt đầu một ngày học, theo giờ JVM. */
    public LocalDateTime startOf(LocalDate day) {
        return day.atTime(dayStartHour, 0).atZone(dayZone).withZoneSameInstant(clock.getZone()).toLocalDateTime();
    }
}
