package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.service.SearchService.Query;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchServiceTest {

    @Test
    void query_shouldTellJapaneseFromLatin_andReadRomaji() {
        Query kanji = Query.of("  学生 ");
        assertThat(kanji.japanese()).isTrue();
        assertThat(kanji.text()).isEqualTo("学生");

        assertThat(Query.of("ガクセイ").kana()).isEqualTo("がくせい");

        Query romaji = Query.of("gakusei");
        assertThat(romaji.japanese()).isFalse();
        assertThat(romaji.romaji()).containsExactly("がくせい");
        assertThat(romaji.latin()).isEqualTo("gakusei");
        assertThat(Query.of("kitte").romaji()).containsExactly("きって");
        assertThat(Query.of("han'i").romaji()).containsExactly("はんい");

        // Có dấu thì không phải romaji; còn lại vẫn tìm theo Hán Việt và nghĩa, không dấu.
        Query vietnamese = Query.of("Học   Sinh");
        assertThat(vietnamese.romaji()).isEmpty();
        assertThat(vietnamese.latin()).isEqualTo("hoc sinh");
        // "sinh" không đọc được thành kana trọn vẹn: chỉ tìm theo Hán Việt và nghĩa.
        assertThat(Query.of("sinh").romaji()).isEmpty();

        assertThat(Query.of("   ")).isNull();
        assertThat(Query.of(null)).isNull();
    }

    @Test
    void unaccent_shouldDropVietnameseMarks_likePostgres() {
        assertThat(SearchService.unaccent("Đường ĐI học")).isEqualTo("duong di hoc");
        assertThat(SearchService.unaccent(null)).isEmpty();
    }

    @Test
    void rank_shouldPutWholeMatchesFirst_thenBeginnings_thenMiddles_thenMeanings() {
        Kanji life = word("生", "SINH", "せい", "Sống");
        Kanji pupil = word("生徒", "SINH ĐỒ", "せいと", "Học sinh");
        Kanji student = word("学生", "HỌC SINH", "がくせい", "Học sinh, sinh viên");
        Kanji teacher = word("先生", "TIÊN SINH", "せんせい", "Giáo viên, bác sĩ");

        Query sinh = Query.of("sinh");
        assertThat(SearchService.rank(life, sinh)).isZero();
        assertThat(SearchService.rank(pupil, sinh)).isEqualTo(1);
        assertThat(SearchService.rank(student, sinh)).isEqualTo(2);

        Query doctor = Query.of("bac si");
        assertThat(SearchService.rank(teacher, doctor)).isEqualTo(1);
        assertThat(SearchService.rank(word("医者", "Y GIẢ", "いしゃ", "Thầy thuốc, người làm bác sĩ"), doctor))
                .isEqualTo(3);

        assertThat(SearchService.rank(student, Query.of("がくせい"))).isZero();
        assertThat(SearchService.rank(student, Query.of("gakusei"))).isZero();
        assertThat(SearchService.rank(student, Query.of("学"))).isEqualTo(1);
        assertThat(SearchService.rank(teacher, Query.of("生"))).isEqualTo(2);
    }

    private static Kanji word(String character, String hanViet, String reading, String meaning) {
        return Kanji.builder().id((long) character.hashCode()).character(character).hanViet(hanViet).reading(reading)
                .meaning(meaning).jlptLevel(JlptLevel.N5).build();
    }
}
