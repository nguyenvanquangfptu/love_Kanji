package com.kanjimastery.backend.repository;

import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.model.Sm2State;
import com.kanjimastery.backend.model.Tag;
import com.kanjimastery.backend.model.User;
import com.kanjimastery.backend.model.UserKanjiSrs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Hàng chờ AI viết câu ví dụ trên PostgreSQL thật: từ đang có người ôn trước, rồi cấp dễ trước; đếm theo cấp. */
class ExampleSentenceQueueIT extends AbstractIntegrationTest {

    @Autowired
    private KanjiRepository kanjiRepository;
    @Autowired
    private TagRepository tagRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserKanjiSrsRepository srsRepository;

    private Long userId;
    private Tag lesson;
    private final List<Long> wordIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        String suffix = String.valueOf(System.nanoTime());
        userId = userRepository.save(User.builder().username("sentence_" + suffix)
                .email("sentence_" + suffix + "@test.local").passwordHash("x").build()).getId();
        lesson = tagRepository.save(Tag.builder().name("IT-CAU-VI-DU-" + suffix).build());
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(userId);
        kanjiRepository.deleteAllById(wordIds);
        tagRepository.deleteById(lesson.getId());
    }

    @Test
    void wordsSomeoneReviews_shouldGetASentenceFirst_thenTheEasierLevels() {
        Map<String, Long> missingBefore = missingByLevel();
        Kanji n5 = word("易", JlptLevel.N5, true);
        Kanji n2Reviewed = word("難", JlptLevel.N2, true);
        Kanji n2 = word("稀", JlptLevel.N2, true);
        word("外", JlptLevel.N5, false);
        srsRepository.save(UserKanjiSrs.builder().userId(userId).kanjiId(n2Reviewed.getId())
                .sm2(new Sm2State(0, new BigDecimal("2.50"))).reviewIntervalDays(0)
                .nextReviewAt(LocalDateTime.now()).build());

        List<Long> queue = kanjiRepository.findWithoutExampleSentence(100_000).stream().map(Kanji::getId).toList();

        assertThat(queue).as("từ chưa gắn bài không cần câu").doesNotContain(wordIds.get(3));
        assertThat(queue.indexOf(n2Reviewed.getId())).as("N2 đang có người ôn đứng trước N5 chưa ai ôn")
                .isLessThan(queue.indexOf(n5.getId()));
        assertThat(queue.indexOf(n5.getId())).isLessThan(queue.indexOf(n2.getId()));

        Map<String, KanjiRepository.MissingSentenceCount> after = kanjiRepository.countWithoutExampleSentenceByLevel()
                .stream().collect(Collectors.toMap(KanjiRepository.MissingSentenceCount::getLevel, row -> row));
        assertThat(after.get("N2").getMissing() - missingBefore.getOrDefault("N2", 0L)).isEqualTo(2);
        assertThat(after.get("N5").getMissing() - missingBefore.getOrDefault("N5", 0L)).isEqualTo(1);
        assertThat(after.get("N2").getInReview()).isPositive();
    }

    private Map<String, Long> missingByLevel() {
        return kanjiRepository.countWithoutExampleSentenceByLevel().stream()
                .collect(Collectors.toMap(KanjiRepository.MissingSentenceCount::getLevel,
                        KanjiRepository.MissingSentenceCount::getMissing));
    }

    private Kanji word(String character, JlptLevel level, boolean inLesson) {
        Kanji word = kanjiRepository.save(Kanji.builder().character(character + System.nanoTime() % 10_000)
                .reading("よみ").meaning("nghĩa").hanViet("").strokeCount(1).jlptLevel(level)
                .tags(inLesson ? Set.of(lesson) : Set.of()).build());
        wordIds.add(word.getId());
        return word;
    }
}
