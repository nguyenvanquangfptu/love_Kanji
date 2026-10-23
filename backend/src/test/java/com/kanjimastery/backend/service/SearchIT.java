package com.kanjimastery.backend.service;

import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.dto.SearchResultResponse;
import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.model.Sm2State;
import com.kanjimastery.backend.model.User;
import com.kanjimastery.backend.model.UserKanjiSrs;
import com.kanjimastery.backend.repository.KanjiRepository;
import com.kanjimastery.backend.repository.UserKanjiSrsRepository;
import com.kanjimastery.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Tìm từ trên PostgreSQL thật: unaccent (V37), cách đọc có ngoặc okurigana, LIKE với ký tự đặc biệt. */
class SearchIT extends AbstractIntegrationTest {

    @Autowired
    private SearchService searchService;
    @Autowired
    private KanjiRepository kanjiRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserKanjiSrsRepository srsRepository;

    private Long userId;
    private final List<Long> wordIds = new ArrayList<>();
    // Từ đặt ra cho test, không trùng dữ liệu mẫu của các migration.
    private Long gloom;
    private Long gloomier;

    @BeforeEach
    void setUp() {
        String suffix = String.valueOf(System.nanoTime());
        userId = userRepository.save(User.builder().username("search_" + suffix).email("search_" + suffix + "@test.local")
                .passwordHash("x").build()).getId();
        gloom = word("鬱鬯", "UẤT SƯỞNG", "うつちょう", "Thử tìm kiếm", JlptLevel.N3);
        gloomier = word("鬱鬯鬯", "UẤT SƯỞNG SƯỞNG", "うつちょうちょう", "Thử tìm kiếm thêm", JlptLevel.N5);
        srsRepository.save(UserKanjiSrs.builder().userId(userId).kanjiId(gloom)
                .sm2(new Sm2State(0, new BigDecimal("2.50"))).reviewIntervalDays(0).nextReviewAt(LocalDateTime.now())
                .build());
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(userId);
        kanjiRepository.deleteAllById(wordIds);
    }

    @Test
    void search_shouldFindWordsWithoutVietnameseMarks_wholeMatchFirst() {
        List<SearchResultResponse> results = searchService.search(userId, "uat suong");

        assertThat(results).extracting(SearchResultResponse::id).startsWith(gloom, gloomier);
        assertThat(results.get(0).inReview()).isTrue();
        assertThat(results.get(1).inReview()).isFalse();
        assertThat(searchService.search(userId, "THU TIM KIEM")).extracting(SearchResultResponse::id)
                .contains(gloom, gloomier);
    }

    @Test
    void search_shouldFindWordsByRomajiKatakanaOrKanji() {
        assertThat(searchService.search(userId, "utsuchou")).extracting(SearchResultResponse::id).startsWith(gloom);
        assertThat(searchService.search(userId, "ウツチョウ")).extracting(SearchResultResponse::id).startsWith(gloom);
        assertThat(searchService.search(userId, "鬱鬯")).extracting(SearchResultResponse::id)
                .startsWith(gloom, gloomier);
    }

    @Test
    void search_shouldMatchReadingsWithOkuriganaInBrackets_andTreatLikeWildcardsAsText() {
        Long see = word("鬱る", "UẤT", "うつ(る)", "Thử okurigana", JlptLevel.N4);

        assertThat(searchService.search(userId, "うつる")).extracting(SearchResultResponse::id).contains(see);
        assertThat(searchService.search(userId, "%")).isEmpty();
        assertThat(searchService.search(userId, "_")).isEmpty();
        assertThat(searchService.search(userId, " ")).isEmpty();
    }

    private Long word(String character, String hanViet, String reading, String meaning, JlptLevel level) {
        Long id = kanjiRepository.save(Kanji.builder().character(character).hanViet(hanViet).reading(reading)
                .meaning(meaning).strokeCount(1).jlptLevel(level).build()).getId();
        wordIds.add(id);
        return id;
    }
}
