package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionSource;
import com.kanjimastery.backend.model.ExamQuestionStatus;
import com.kanjimastery.backend.model.ExampleSentenceSource;
import com.kanjimastery.backend.model.JlptQuestionType;
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
    private static final JlptLevel LEVEL = JlptLevel.N1;

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
                {"学校", "がっこう", "毎日歩いて学校へ行きます。", "Trường học"},
                {"先生", "せんせい", "分からないことは先生に聞きます。", "Giáo viên"},
                {"病院", "びょういん", "姉は駅の近くの病院で働きます。", "Bệnh viện"},
                {"電車", "でんしゃ", "毎朝七時の電車に乗ります。", "Tàu điện"},
        };
        for (String[] word : words) {
            wordIds.add(kanjiRepository.save(Kanji.builder()
                    .character(word[0]).reading(word[1]).exampleSentence(word[2]).meaning(word[3])
                    .hanViet("").jlptLevel(JlptLevel.N5).strokeCount(10).tags(new HashSet<>(Set.of(lesson)))
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
    void generate_shouldSaveFourQuestionsPerWord_andNothingMoreTheSecondTime() {
        // Mỗi từ: đọc, viết, điền từ (4 danh từ còn lại làm đáp án nhiễu), nghĩa.
        assertThat(generator.generate(LEVEL.name()).created()).isEqualTo(20);
        assertThat(generator.generate(LEVEL.name()).created()).isZero();

        List<Long> ids = questionRepository.findAll().stream()
                .filter(question -> LEVEL.equals(question.getJlptLevel()))
                .map(ExamQuestion::getId)
                .toList();
        List<ExamQuestion> saved = questionRepository.findAllWithWordsByIdIn(ids);
        assertThat(saved).hasSize(20).allSatisfy(question -> {
            assertThat(question.getSource()).isEqualTo(ExamQuestionSource.GENERATED);
            assertThat(question.getKanjiIds()).hasSize(1).isSubsetOf(wordIds);
            assertThat(question.getSentence()).isNotBlank();
            assertThat(question.getSentence()).contains(JlptQuestionType.CONTEXT.equals(question.getQuestionType())
                    ? ExamQuestionGenerator.BLANK
                    : question.getHighlight());
        });
        assertThat(saved).filteredOn(question -> JlptQuestionType.CONTEXT.equals(question.getQuestionType())).hasSize(5);
        assertThat(questionRepository.generatedQuestionWords(LEVEL.name())).hasSize(20);
    }

    /** V42: câu do AI viết (lưu qua saveExampleSentenceIfAbsent) mang nguồn AI; câu thi dựng từ câu đó chờ duyệt. */
    @Test
    void questionsBuiltOnAnAiSentence_shouldWaitForReview_whileTheOthersGoStraightIn() {
        Kanji station = kanjiRepository.save(Kanji.builder().character("駅").reading("えき").meaning("Nhà ga")
                .hanViet("").jlptLevel(JlptLevel.N5).strokeCount(14).tags(new HashSet<>(Set.of(lesson))).build());
        wordIds.add(station.getId());
        assertThat(kanjiRepository.findWithoutExampleSentence(1000)).extracting(Kanji::getId).contains(station.getId());

        assertThat(kanjiRepository.saveExampleSentenceIfAbsent(station.getId(), "家から駅まで歩きます。")).isEqualTo(1);
        assertThat(kanjiRepository.saveExampleSentenceIfAbsent(station.getId(), "二つ目の文。")).as("đã có câu thì giữ")
                .isZero();
        assertThat(kanjiRepository.findById(station.getId()).orElseThrow().getExampleSentenceSource())
                .isEqualTo(ExampleSentenceSource.AI);
        assertThat(kanjiRepository.findWithoutExampleSentence(1000)).extracting(Kanji::getId).doesNotContain(station.getId());

        generator.generate(LEVEL.name());

        List<ExamQuestion> generated = questionRepository.findAllWithWordsByIdIn(questionRepository.findAll().stream()
                .filter(question -> LEVEL.equals(question.getJlptLevel())).map(ExamQuestion::getId).toList());
        assertThat(generated).filteredOn(question -> question.getKanjiIds().contains(station.getId()))
                .isNotEmpty()
                .allSatisfy(question -> {
                    assertThat(question.getStatus()).isEqualTo(ExamQuestionStatus.DRAFT);
                    assertThat(question.getReviewNote()).isEqualTo(ExamQuestionGenerator.AI_SENTENCE_NOTE);
                });
        assertThat(generated).filteredOn(question -> !question.getKanjiIds().contains(station.getId()))
                .isNotEmpty()
                .allSatisfy(question -> assertThat(question.getStatus()).isEqualTo(ExamQuestionStatus.APPROVED));
    }
}
