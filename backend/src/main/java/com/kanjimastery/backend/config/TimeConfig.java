package com.kanjimastery.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Đồng hồ dùng chung: mọi "bây giờ" lấy từ đây để test cố định được thời gian. */
@Configuration
public class TimeConfig {

    /** Theo múi giờ JVM - các cột thời gian ({@code LocalDateTime}) vẫn ghi và so theo giờ JVM như trước. */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
