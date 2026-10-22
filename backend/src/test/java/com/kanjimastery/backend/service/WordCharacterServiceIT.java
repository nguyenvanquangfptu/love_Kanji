package com.kanjimastery.backend.service;

import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.dto.CharacterDetailResponse;
import com.kanjimastery.backend.dto.WordCharacterResponse;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.exception.ResourceNotFoundException;
import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.model.Sm2State;
import com.kanjimastery.backend.model.User;
import com.kanjimastery.backend.model.UserKanjiSrs;
import com.kanjimastery.backend.model.WordCharacter;
import com.kanjimastery.backend.repository.KanjiRepository;
import com.kanjimastery.backend.repository.UserKanjiSrsRepository;
import com.kanjimastery.backend.repository.UserRepository;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/** Hán Việt từng chữ và các từ cùng chữ trên PostgreSQL thật (V35). */
class WordCharacterServiceIT extends AbstractIntegrationTest {

    // Chữ hiếm, không có trong dữ liệu mẫu của các migration.
    private static final String CHARACTER = "鬱";

    @Autowired
    private WordCharacterService service;
    @Autowired
    private KanjiRepository kanjiRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserKanjiSrsRepository srsRepository;

    private Long userId;
    private final List<Long> wordIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        String suffix = String.valueOf(System.nanoTime());
        userId = userRepository.save(User.builder().username("chars_" + suffix).email("chars_" + suffix + "@test.local")
                .passwordHash("x").build()).getId();
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(userId);
        kanjiRepository.deleteAllById(wordIds);
    }

    @Test
    void detail_shouldGroupTheWordsByTheSyllableOfTheCharacter_mostWordsFirst_easiestLevelFirst() {
        Kanji gloomN3 = word("憂鬱", JlptLevel.N3, part("憂", "ƯU"), part(CHARACTER, "UẤT"));
        Kanji gloomN5 = word("鬱病", JlptLevel.N5, part(CHARACTER, "UẤT"), part("病", "BỆNH"));
        Kanji other = word("鬱金", JlptLevel.N4, part(CHARACTER, "UẨN"), part("金", "KIM"));
        // Cùng cách viết, khác nghĩa (hai dòng): là hai từ, không gộp.
        Kanji gloomAgain = word("憂鬱", JlptLevel.N2, part("憂", "ƯU"), part(CHARACTER, "UẤT"));
        srsRepository.save(card(gloomN5.getId()));

        CharacterDetailResponse detail = service.detail(userId, CHARACTER);

        assertThat(detail.character()).isEqualTo(CHARACTER);
        assertThat(detail.readings()).extracting(CharacterDetailResponse.Reading::hanViet).containsExactly("UẤT", "UẨN");
        assertThat(detail.readings().get(0).words())
                .extracting(CharacterDetailResponse.Word::id, CharacterDetailResponse.Word::jlptLevel,
                        CharacterDetailResponse.Word::inReview)
                .containsExactly(tuple(gloomN5.getId(), "N5", true), tuple(gloomN3.getId(), "N3", false),
                        tuple(gloomAgain.getId(), "N2", false));
        assertThat(detail.readings().get(1).words()).extracting(CharacterDetailResponse.Word::id)
                .containsExactly(other.getId());
    }

    @Test
    void detail_shouldRefuseAnythingButOneKanji_andSayWhenNoWordHasIt() {
        assertThatThrownBy(() -> service.detail(userId, "鬱病")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.detail(userId, "あ")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.detail(userId, CHARACTER)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void partsOf_shouldListTheCharactersOfEachWordInOrder_leavingOutWordsWithoutAny() {
        Kanji gloom = word("憂鬱", JlptLevel.N3, part("憂", "ƯU"), part(CHARACTER, "UẤT"));
        Kanji kanaOnly = word("テレビ", JlptLevel.N5);

        assertThat(service.partsOf(List.of(gloom.getId(), kanaOnly.getId())))
                .containsOnlyKeys(gloom.getId())
                .extractingByKey(gloom.getId(), InstanceOfAssertFactories.list(WordCharacterResponse.class))
                .containsExactly(new WordCharacterResponse("憂", "ƯU"), new WordCharacterResponse(CHARACTER, "UẤT"));
        assertThat(service.partsOf(List.of())).isEmpty();
        assertThatThrownBy(() -> service.partsOf(IntStream.rangeClosed(1, 201).mapToObj(Long::valueOf).toList()))
                .isInstanceOf(BadRequestException.class);
    }

    private Kanji word(String character, JlptLevel level, WordCharacter... parts) {
        Kanji word = kanjiRepository.save(Kanji.builder().character(character).hanViet("").meaning("IT").strokeCount(1)
                .jlptLevel(level).characterParts(new ArrayList<>(List.of(parts))).build());
        wordIds.add(word.getId());
        return word;
    }

    private static WordCharacter part(String character, String hanViet) {
        return new WordCharacter(character, hanViet);
    }

    private UserKanjiSrs card(Long kanjiId) {
        return UserKanjiSrs.builder().userId(userId).kanjiId(kanjiId).sm2(new Sm2State(0, new BigDecimal("2.50")))
                .reviewIntervalDays(0).nextReviewAt(LocalDateTime.now()).build();
    }
}
