package com.kanjimastery.backend.repository;

import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.model.Tag;
import com.kanjimastery.backend.model.User;
import com.kanjimastery.backend.model.UserKanjiSrs;
import com.kanjimastery.backend.repository.TagRepository.LessonToAdd;
import com.kanjimastery.backend.repository.TagRepository.ScopeProgress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Các truy vấn phạm vi mục tiêu học (theo tiền tố tên bài N5-, N4-...) chạy trên PostgreSQL thật. */
class TagRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private TagRepository tagRepository;
    @Autowired
    private KanjiRepository kanjiRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserKanjiSrsRepository srsRepository;

    private Long userId;
    private final List<Long> kanjiIds = new ArrayList<>();
    private final List<Long> tagIds = new ArrayList<>();
    private Kanji learned;
    private Kanji added;
    private Kanji untouched;

    @BeforeEach
    void setUp() {
        String suffix = String.valueOf(System.nanoTime());
        userId = userRepository.save(User.builder()
                .username("scope_" + suffix)
                .email("scope_" + suffix + "@test.local")
                .passwordHash("x")
                .build()).getId();
        Tag n5First = tag("N5-01");
        Tag n5Second = tag("N5-02");
        Tag n4 = tag("N4-26");
        Tag other = tag("Khác-" + suffix);

        learned = word("学", n5First);
        added = word("習", n5First);
        untouched = word("読", n5Second);
        word("書", n4);
        word("話", n4);
        word("他", other);

        srsRepository.save(card(learned.getId(), LocalDateTime.now().minusDays(1)));
        srsRepository.save(card(added.getId(), null));
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(userId);
        kanjiRepository.deleteAllById(kanjiIds);
        tagRepository.deleteAllById(tagIds);
    }

    @Test
    void scopeProgress_shouldCountWordsOfTheGivenLevels_andThoseLearnedAtLeastOnce() {
        ScopeProgress n5 = tagRepository.scopeProgress(userId, List.of("N5"));
        ScopeProgress n5AndN4 = tagRepository.scopeProgress(userId, List.of("N5", "N4"));

        // 習 đã có trong Ôn tập nhưng chưa học lần nào: chưa tính là đã học.
        assertThat(n5.getTotal()).isEqualTo(3);
        assertThat(n5.getStarted()).isEqualTo(1);
        assertThat(n5AndN4.getTotal()).isEqualTo(5);
        assertThat(n5AndN4.getStarted()).isEqualTo(1);
    }

    @Test
    void nextLessonToAdd_shouldGoThroughN5BeforeN4_andSkipLessonsAlreadyInReview() {
        // Theo tên thì N4-26 đứng trước N5-02, nhưng N5 phải học trước.
        assertThat(tagRepository.nextLessonToAdd(userId, List.of("N5", "N4")))
                .get().extracting(LessonToAdd::getName, LessonToAdd::getWords).containsExactly("N5-02", 1L);

        srsRepository.save(card(untouched.getId(), null));

        assertThat(tagRepository.nextLessonToAdd(userId, List.of("N5", "N4")))
                .get().extracting(LessonToAdd::getName, LessonToAdd::getWords).containsExactly("N4-26", 2L);
        assertThat(tagRepository.nextLessonToAdd(userId, List.of("N5"))).isEmpty();
    }

    private Tag tag(String name) {
        Tag tag = tagRepository.save(Tag.builder().name(name).build());
        tagIds.add(tag.getId());
        return tag;
    }

    private Kanji word(String character, Tag tag) {
        Kanji kanji = kanjiRepository.save(Kanji.builder()
                .character(character + System.nanoTime() % 10_000)
                .hanViet("")
                .strokeCount(1)
                .jlptLevel("N5")
                .meaning("nghĩa")
                .tags(Set.of(tag))
                .build());
        kanjiIds.add(kanji.getId());
        return kanji;
    }

    private UserKanjiSrs card(Long kanjiId, LocalDateTime lastReviewedAt) {
        return UserKanjiSrs.builder()
                .userId(userId)
                .kanjiId(kanjiId)
                .repetitionCount(lastReviewedAt == null ? 0 : 1)
                .easinessFactor(new BigDecimal("2.50"))
                .reviewIntervalDays(lastReviewedAt == null ? 0 : 1)
                .nextReviewAt(LocalDateTime.now())
                .lastReviewedAt(lastReviewedAt)
                .build();
    }
}
