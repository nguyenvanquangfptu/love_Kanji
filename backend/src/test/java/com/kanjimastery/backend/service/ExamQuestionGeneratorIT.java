package com.kanjimastery.backend.service;

import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionSource;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.model.Tag;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.KanjiRepository;
import com.kanjimastery.backend.repository.TagRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Sinh câu thi cho một cấp độ trên PostgreSQL thật: lưu kèm từ vựng, chạy lại không sinh trùng. */
class ExamQuestionGeneratorIT extends AbstractIntegrationTest {

    /** Cấp độ giả để không đụng tới các bài N5/N4 thật trong dữ liệu mẫu. */
    private static final String LEVEL = "N9";

    @Autowired
    private ExamQuestionGenerator generator;
    @Autowired
    private ExamQuestionRepository questionRepository;
    @Autowired
    private KanjiRepository kanjiRepository;
    @Autowired
    private TagRepository tagRepository;

    private Tag lesson;
    private final List<Long> wordIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        lesson = tagRepository.save(Tag.builder().name(LEVEL + "-01").build());
        String[][] words = {
                {"新聞", "しんぶん", "毎朝新聞を読みます。", "Báo"},
                {"学校", "がっこう", "学校へ行きます。", "Trường học"},
                {"先生", "せんせい", "先生に聞きます。", "Giáo viên"},
                {"病院", "びょういん", "病院で働きます。", "Bệnh viện"},
                {"電車", "でんしゃ", "電車に乗ります。", "Tàu điện"},
        };
        for (String[] word : words) {
            wordIds.add(kanjiRepository.save(Kanji.builder()
                    .character(word[0]).reading(word[1]).exampleSentence(word[2]).meaning(word[3])
                    .hanViet("").jlptLevel("N5").strokeCount(10).tags(new HashSet<>(Set.of(lesson)))
                    .build()).getId());
        }
    }

    @AfterEach
    void tearDown() {
        // Xoá từ thì liên kết câu - từ cũng mất (ON DELETE CASCADE); câu thi sinh ra xoá riêng.
        questionRepository.deleteAll(questionRepository.findAll().stream()
                .filter(question -> LEVEL.equals(question.getJlptLevel()))
                .toList());
        kanjiRepository.deleteAllById(wordIds);
        tagRepository.delete(lesson);
    }

    @Test
    void generate_shouldSaveThreeQuestionsPerWord_andNothingMoreTheSecondTime() {
        assertThat(generator.generate(LEVEL).created()).isEqualTo(15);
        assertThat(generator.generate(LEVEL).created()).isZero();

        List<Long> ids = questionRepository.findAll().stream()
                .filter(question -> LEVEL.equals(question.getJlptLevel()))
                .map(ExamQuestion::getId)
                .toList();
        List<ExamQuestion> saved = questionRepository.findAllWithWordsByIdIn(ids);
        assertThat(saved).hasSize(15).allSatisfy(question -> {
            assertThat(question.getSource()).isEqualTo(ExamQuestionSource.GENERATED);
            assertThat(question.getKanjiIds()).hasSize(1).isSubsetOf(wordIds);
            assertThat(question.getSentence()).isNotBlank();
            assertThat(question.getSentence()).contains(question.getHighlight());
        });
        assertThat(questionRepository.generatedQuestionWords(LEVEL)).hasSize(15);
    }
}
