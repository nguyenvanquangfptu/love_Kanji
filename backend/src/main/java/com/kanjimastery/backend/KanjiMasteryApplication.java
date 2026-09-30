package com.kanjimastery.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableCaching
public class KanjiMasteryApplication {

    public static void main(String[] args) {
        SpringApplication.run(KanjiMasteryApplication.class, args);
    }
}
