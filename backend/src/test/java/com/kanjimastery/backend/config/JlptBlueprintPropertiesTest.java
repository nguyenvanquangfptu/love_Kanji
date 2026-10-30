package com.kanjimastery.backend.config;

import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.ExamSection;
import com.kanjimastery.backend.model.JlptQuestionType;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/** Đọc đúng file cấu trúc đề thật (jlpt-blueprints.yml): khoá viết hoa có gạch dưới, thứ tự các 問題. */
class JlptBlueprintPropertiesTest {

    @Test
    void blueprintFile_shouldBindEveryLevel_withTheOfficialQuestionCountsInMondaiOrder() throws IOException {
        JlptBlueprintProperties blueprints = load();

        assertThat(blueprints.getLevels()).containsOnlyKeys(JlptLevel.N5, JlptLevel.N4, JlptLevel.N3);
        JlptBlueprintProperties.Section vocabulary = blueprints.section(JlptLevel.N4, ExamSection.VOCABULARY).orElseThrow();
        assertThat(vocabulary.getMinutes()).isEqualTo(25);
        assertThat(vocabulary.getQuestions()).containsExactly(
                entry(JlptQuestionType.KANJI_READING, 7), entry(JlptQuestionType.ORTHOGRAPHY, 5),
                entry(JlptQuestionType.CONTEXT, 8), entry(JlptQuestionType.PARAPHRASE, 4),
                entry(JlptQuestionType.USAGE, 4));
        assertThat(vocabulary.plannedQuestions()).isEqualTo(28);
        assertThat(blueprints.section(JlptLevel.N5, ExamSection.GRAMMAR).orElseThrow().types()).containsExactly(
                JlptQuestionType.GRAMMAR_FORM, JlptQuestionType.SENTENCE_ORDER, JlptQuestionType.TEXT_GRAMMAR);
        assertThat(blueprints.section(JlptLevel.N3, ExamSection.VOCABULARY).orElseThrow().plannedQuestions()).isEqualTo(35);
        assertThat(blueprints.section(JlptLevel.N2, ExamSection.VOCABULARY)).isEmpty();
    }

    @Test
    void blueprintFile_shouldOnlyUseKnownSectionsAndQuestionTypes_inTheRealOrder() throws IOException {
        Set<JlptQuestionType> types = EnumSet.allOf(JlptQuestionType.class);

        load().getLevels().forEach((level, blueprint) -> {
            // Đọc hiểu mới có ở N3; luôn đứng sau Kiến thức ngôn ngữ như đề thật.
            List<ExamSection> expected = level == JlptLevel.N3
                    ? List.of(ExamSection.VOCABULARY, ExamSection.GRAMMAR, ExamSection.READING)
                    : List.of(ExamSection.VOCABULARY, ExamSection.GRAMMAR);
            assertThat(blueprint.getSections()).extracting(JlptBlueprintProperties.Section::getName)
                    .as(level.name()).containsExactlyElementsOf(expected);
            blueprint.getSections().forEach(section -> {
                assertThat(section.getMinutes()).as(level + " " + section.getName()).isPositive();
                assertThat(section.getQuestions().keySet()).as(level + " " + section.getName()).isSubsetOf(types);
                assertThat(section.getQuestions().values()).allSatisfy(count -> assertThat(count).isPositive());
                // Mỗi dạng câu nằm đúng phần thi của nó.
                assertThat(section.getQuestions().keySet()).as(level + " " + section.getName())
                        .allSatisfy(type -> assertThat(type.section()).isEqualTo(section.getName()));
            });
        });
    }

    @Test
    void readingN3_shouldTakeWholePassages_andContinueTheNumberingOfGrammar() throws IOException {
        JlptBlueprintProperties blueprints = load();

        JlptBlueprintProperties.Section reading = blueprints.section(JlptLevel.N3, ExamSection.READING).orElseThrow();
        assertThat(reading.getMinutes()).isEqualTo(45);
        assertThat(reading.isFullSetOnly()).isTrue();
        assertThat(reading.getQuestions()).containsExactly(
                entry(JlptQuestionType.READING_SHORT, 4), entry(JlptQuestionType.READING_MID, 6),
                entry(JlptQuestionType.READING_LONG, 4), entry(JlptQuestionType.INFO_SEARCH, 2));
        // 4 bài ngắn × 1 câu, 2 bài trung × 3, 1 bài dài × 4, 1 bài tìm thông tin × 2: 8 bài, 16 câu.
        assertThat(reading.getPerPassage()).containsExactly(
                entry(JlptQuestionType.READING_SHORT, 1), entry(JlptQuestionType.READING_MID, 3),
                entry(JlptQuestionType.READING_LONG, 4), entry(JlptQuestionType.INFO_SEARCH, 2));
        assertThat(reading.plannedQuestions()).isEqualTo(16);
        // Ngữ pháp N3 có 問題1-3 và 23 câu: Đọc hiểu là 問題4-7, câu 24-39.
        assertThat(blueprints.firstMondaiNumber(JlptLevel.N3, ExamSection.READING)).isEqualTo(4);
        assertThat(blueprints.firstQuestionNumber(JlptLevel.N3, ExamSection.READING)).isEqualTo(24);
        assertThat(blueprints.firstMondaiNumber(JlptLevel.N3, ExamSection.GRAMMAR)).isEqualTo(1);
        assertThat(blueprints.firstQuestionNumber(JlptLevel.N3, ExamSection.VOCABULARY)).isEqualTo(1);
    }

    @Test
    void blueprintFile_shouldSplitEveryPassageTypeIntoWholePassages() throws IOException {
        load().getLevels().forEach((level, blueprint) -> blueprint.getSections().forEach(section ->
                section.getPerPassage().forEach((type, perPassage) -> {
                    assertThat(section.getQuestions()).as(level + " " + type).containsKey(type);
                    assertThat(type.usesPassage()).as(level + " " + type).isTrue();
                    assertThat(perPassage).as(level + " " + type).isPositive();
                    assertThat(section.getQuestions().get(type) % perPassage).as(level + " " + type).isZero();
                })));
    }

    @Test
    void numbering_shouldIgnoreACycleInTheConfiguration() {
        JlptBlueprintProperties blueprints = new JlptBlueprintProperties();
        JlptBlueprintProperties.Section grammar = new JlptBlueprintProperties.Section();
        grammar.setName(ExamSection.GRAMMAR);
        grammar.getQuestions().put(JlptQuestionType.GRAMMAR_FORM, 5);
        grammar.setNumberingAfter(ExamSection.READING);
        JlptBlueprintProperties.Section reading = new JlptBlueprintProperties.Section();
        reading.setName(ExamSection.READING);
        reading.getQuestions().put(JlptQuestionType.READING_SHORT, 2);
        reading.setNumberingAfter(ExamSection.GRAMMAR);
        JlptBlueprintProperties.Level level = new JlptBlueprintProperties.Level();
        level.setSections(List.of(grammar, reading));
        blueprints.getLevels().put(JlptLevel.N3, level);

        // Không lặp vô hạn: Ngữ pháp đánh tiếp theo Đọc hiểu bị bỏ qua, Đọc hiểu chỉ đánh tiếp sau 5 câu Ngữ pháp.
        assertThat(blueprints.firstQuestionNumber(JlptLevel.N3, ExamSection.READING)).isEqualTo(6);
    }

    private static JlptBlueprintProperties load() throws IOException {
        List<org.springframework.core.env.PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("jlpt-blueprints", new ClassPathResource("jlpt-blueprints.yml"));
        return new Binder(ConfigurationPropertySources.from(sources))
                .bind("app.jlpt", JlptBlueprintProperties.class)
                .orElseThrow(IllegalStateException::new);
    }
}
