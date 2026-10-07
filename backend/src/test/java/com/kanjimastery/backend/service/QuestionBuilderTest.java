package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.QuizDirection;
import com.kanjimastery.backend.model.Kanji;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static com.kanjimastery.backend.model.QuizDirection.KANJI_TO_READING;
import static com.kanjimastery.backend.model.QuizDirection.MEANING;
import static com.kanjimastery.backend.model.QuizDirection.READING_TO_KANJI;
import static org.assertj.core.api.Assertions.assertThat;

class QuestionBuilderTest {

    /** Dựng câu hỏi không cần truy vấn kho (existingWords truyền thẳng vào). */
    private final QuestionBuilder builder = new QuestionBuilder(null, new QuizDistractorGenerator());

    private final List<Kanji> pool = LongStream.rangeClosed(100, 110)
            .mapToObj(id -> word(id, "語" + id, "ご" + id, "nghĩa " + id))
            .toList();

    @Test
    void build_shouldUnderlineTheWordInItsSentence_inJlptStyle() {
        Kanji subway = word(1L, "地下鉄", "ちかてつ", "Tàu điện ngầm");

        // Đứng cạnh một từ Hán khác (毎朝) vẫn là một từ riêng.
        assertThat(sentence(subway, KANJI_TO_READING, "毎朝地下鉄に乗ります。")).isEqualTo("毎朝地下鉄に乗ります。");
        assertThat(sentence(subway, READING_TO_KANJI, "毎朝地下鉄に乗ります。")).isEqualTo("毎朝ちかてつに乗ります。");
    }

    @Test
    void build_shouldDropTheSentence_whenASingleKanjiWordIsPartOfAnotherWord() {
        Kanji day = word(1L, "日", "ひ", "Ngày");
        Kanji year = word(2L, "年", "とし", "Năm, tuổi");

        // 日 ở 今日 và 日曜日 đọc khác hẳn ひ; thay nhầm còn làm lộ đáp án câu hỏi viết.
        assertThat(sentence(day, KANJI_TO_READING, "今日は日曜日だ。")).isNull();
        assertThat(sentence(day, READING_TO_KANJI, "今日は日曜日だ。")).isNull();
        assertThat(sentence(year, MEANING, "私の妹は今年で五歳になる。")).isNull();
        // Đứng riêng thì dùng được.
        assertThat(sentence(day, KANJI_TO_READING, "その日は雨だった。")).isEqualTo("その日は雨だった。");
    }

    @Test
    void build_shouldDropTheSentence_whenTheWordAppearsTwice_orItsReadingIsAlreadyThere() {
        Kanji library = word(1L, "図書館", "としょかん", "Thư viện");
        Kanji tooth = word(2L, "歯", "は", "Răng");

        assertThat(sentence(library, KANJI_TO_READING, "図書館で本を借りて、図書館に返す。")).isNull();
        // Hỏi cách viết của は trong 私は歯を磨く: không biết gạch chân は nào.
        assertThat(sentence(tooth, READING_TO_KANJI, "私は歯を磨く。")).isNull();
        assertThat(sentence(tooth, KANJI_TO_READING, "私は歯を磨く。")).isEqualTo("私は歯を磨く。");
    }

    private String sentence(Kanji word, QuizDirection direction, String exampleSentence) {
        QuestionBuilder.BuiltQuestion question = builder.build(builder.plan(word, direction, List.of()),
                exampleSentence, pool, Map.of());
        assertThat(question.choices()).hasSize(QuestionBuilder.CHOICES);
        return question.sentence();
    }

    private static Kanji word(Long id, String character, String reading, String meaning) {
        return Kanji.builder().id(id).character(character).reading(reading).meaning(meaning).hanViet("")
                .jlptLevel(JlptLevel.N5).strokeCount(5).build();
    }
}
