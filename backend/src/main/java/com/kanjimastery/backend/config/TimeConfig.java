package com.kanjimastery.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Đồng hồ dùng chung: mọi "bây giờ" lấy từ đây để test cố định được thời gian. */
@Slf4j
@Configuration
public class TimeConfig {

    /**
     * Theo múi giờ JVM - các cột thời gian ({@code LocalDateTime}) ghi và so theo giờ JVM, và JSON trả thời gian không kèm
     * múi giờ nên JVM phải chạy giờ Việt Nam ở mọi nơi (docker-compose đặt {@code -Duser.timezone}).
     */
    @Bean
    public Clock clock() {
        Clock clock = Clock.systemDefaultZone();
        log.info("Múi giờ JVM: {}", clock.getZone());
        return clock;
    }
}
