package com.kanjimastery.backend.config;

import com.kanjimastery.backend.model.ExamSection;
import com.kanjimastery.backend.model.JlptQuestionType;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/** Đọc đúng file cấu trúc đề thật (jlpt-blueprints.yml): khoá viết hoa có gạch dưới, thứ tự các 問題. */
class JlptBlueprintPropertiesTest {

    @Test
    void blueprintFile_shouldBindEveryLevel_withTheOfficialQuestionCountsInMondaiOrder() throws IOException {
        JlptBlueprintProperties blueprints = load();

        assertThat(blueprints.getLevels()).containsOnlyKeys("N5", "N4", "N3");
        JlptBlueprintProperties.Section vocabulary = blueprints.section("N4", ExamSection.VOCABULARY).orElseThrow();
        assertThat(vocabulary.getMinutes()).isEqualTo(25);
        assertThat(vocabulary.getQuestions()).containsExactly(
                entry(JlptQuestionType.KANJI_READING, 7), entry(JlptQuestionType.ORTHOGRAPHY, 5),
                entry(JlptQuestionType.CONTEXT, 8), entry(JlptQuestionType.PARAPHRASE, 4),
                entry(JlptQuestionType.USAGE, 4));
        assertThat(vocabulary.plannedQuestions()).isEqualTo(28);
        assertThat(blueprints.section("N5", ExamSection.GRAMMAR).orElseThrow().types()).containsExactly(
                JlptQuestionType.GRAMMAR_FORM, JlptQuestionType.SENTENCE_ORDER, JlptQuestionType.TEXT_GRAMMAR);
        assertThat(blueprints.section("N3", ExamSection.VOCABULARY).orElseThrow().plannedQuestions()).isEqualTo(35);
        assertThat(blueprints.section("N2", ExamSection.VOCABULARY)).isEmpty();
    }

    @Test
    void blueprintFile_shouldOnlyUseKnownSectionsAndQuestionTypes_inTheRealOrder() throws IOException {
        Set<String> types = Arrays.stream(JlptQuestionType.class.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()) && field.getType() == String.class)
                .map(field -> {
                    try {
                        return (String) field.get(null);
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException(e);
                    }
                })
                .collect(Collectors.toSet());

        load().getLevels().forEach((level, blueprint) -> {
            assertThat(blueprint.getSections()).extracting(JlptBlueprintProperties.Section::getName)
                    .as(level).containsExactly(ExamSection.VOCABULARY, ExamSection.GRAMMAR);
            blueprint.getSections().forEach(section -> {
                assertThat(section.getMinutes()).as(level + " " + section.getName()).isPositive();
                assertThat(section.getQuestions().keySet()).as(level + " " + section.getName()).isSubsetOf(types);
                assertThat(section.getQuestions().values()).allSatisfy(count -> assertThat(count).isPositive());
            });
        });
    }

    private static JlptBlueprintProperties load() throws IOException {
        List<org.springframework.core.env.PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("jlpt-blueprints", new ClassPathResource("jlpt-blueprints.yml"));
        return new Binder(ConfigurationPropertySources.from(sources))
                .bind("app.jlpt", JlptBlueprintProperties.class)
                .orElseThrow(IllegalStateException::new);
    }
}
