package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.QuizDirection;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionSource;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.ExamQuestionRepository.GeneratedQuestionWord;
import com.kanjimastery.backend.repository.KanjiRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.kanjimastery.backend.model.JlptQuestionType.CONTEXT;
import static com.kanjimastery.backend.model.JlptQuestionType.KANJI_READING;
import static com.kanjimastery.backend.model.JlptQuestionType.ORTHOGRAPHY;
import static com.kanjimastery.backend.model.QuizDirection.MEANING;
import static com.kanjimastery.backend.model.QuizDirection.READING_TO_KANJI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamQuestionGeneratorTest {

    @Mock
    private KanjiRepository kanjiRepository;
    @Mock
    private ExamQuestionRepository questionRepository;

    private ExamQuestionGenerator generator;

    private final Kanji newspaper = word(1L, "新聞", "しんぶん", "毎朝新聞を読みます。", "Báo");
    private final Kanji school = word(2L, "学校", "がっこう", "毎日歩いて学校へ行きます。", "Trường học");
    private final Kanji teacher = word(3L, "先生", "せんせい", "分からないことは先生に聞きます。", "Giáo viên");
    private final Kanji see = word(4L, "見る", "み(る)", "テレビを見る。", "Xem, nhìn");
    private final Kanji yes = word(5L, "はい", null, null, "Vâng, có");
    private final Kanji hospital = word(6L, "病院", "びょういん", null, "Bệnh viện");

    @BeforeEach
    void setUp() {
        generator = new ExamQuestionGenerator(kanjiRepository, questionRepository,
                new QuestionBuilder(kanjiRepository, new QuizDistractorGenerator()));
        lenient().when(kanjiRepository.findAllByCharacterIn(anyCollection())).thenReturn(List.of());
        lenient().when(questionRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void generate_shouldAskEveryJlptTypeTheWordAllows_andMeaningForEveryWord_skippingWhatExists() {
        when(kanjiRepository.findAllByTagNamePrefix("N4-%"))
                .thenReturn(List.of(newspaper, school, teacher, see, yes, hospital));
        when(questionRepository.generatedQuestionWords("N4")).thenReturn(List.of(generated(2L, MEANING.name())));

        ExamQuestionGenerator.Result result = generator.generate("n4");

        List<ExamQuestion> saved = savedQuestions();
        // 新聞, 先生: đọc + viết + điền từ + nghĩa; 学校 đã có câu nghĩa; 見る: cách đọc "み(る)" không thay vào câu
        // được nên không hỏi viết, và là động từ duy nhất nên không đủ đáp án nhiễu cho câu điền từ; はい không có
        // chữ Hán, 病院 không có câu ví dụ: chỉ hỏi nghĩa.
        assertThat(saved).extracting(question -> question.getKanjiIds().iterator().next(), ExamQuestionGeneratorTest::kind)
                .containsExactlyInAnyOrder(
                        tuple(1L, KANJI_READING), tuple(1L, ORTHOGRAPHY), tuple(1L, CONTEXT), tuple(1L, MEANING.name()),
                        tuple(2L, KANJI_READING), tuple(2L, ORTHOGRAPHY), tuple(2L, CONTEXT),
                        tuple(3L, KANJI_READING), tuple(3L, ORTHOGRAPHY), tuple(3L, CONTEXT), tuple(3L, MEANING.name()),
                        tuple(4L, KANJI_READING), tuple(4L, MEANING.name()),
                        tuple(5L, MEANING.name()), tuple(6L, MEANING.name()));
        assertThat(result).isEqualTo(new ExamQuestionGenerator.Result("N4", 6, 15));
        assertThat(saved).allSatisfy(question -> {
            assertThat(question.getJlptLevel()).isEqualTo("N4");
            assertThat(question.getSource()).isEqualTo(ExamQuestionSource.GENERATED);
            assertThat(List.of(question.getOptionA(), question.getOptionB(), question.getOptionC(),
                    question.getOptionD())).doesNotHaveDuplicates();
        });

        ExamQuestion reading = question(saved, 1L, KANJI_READING);
        assertThat(reading.getSentence()).isEqualTo("毎朝新聞を読みます。");
        assertThat(reading.getHighlight()).isEqualTo("新聞");
        assertThat(correctAnswer(reading)).isEqualTo("しんぶん");

        ExamQuestion writing = question(saved, 1L, ORTHOGRAPHY);
        assertThat(writing.getSentence()).isEqualTo("毎朝しんぶんを読みます。");
        assertThat(writing.getHighlight()).isEqualTo("しんぶん");
        assertThat(correctAnswer(writing)).isEqualTo("新聞");

        ExamQuestion meaning = question(saved, 1L, MEANING.name());
        assertThat(meaning.getQuestionText()).isEqualTo("Từ 「新聞」 (しんぶん) có nghĩa là gì?");
        // Câu hỏi nghĩa vẫn kèm câu ví dụ làm ngữ cảnh, như trắc nghiệm.
        assertThat(meaning.getSentence()).isEqualTo("毎朝新聞を読みます。");
        assertThat(meaning.getHighlight()).isEqualTo("新聞");
        assertThat(question(saved, 6L, MEANING.name()).getSentence()).isNull();
        assertThat(correctAnswer(meaning)).isEqualTo("Báo");
        assertThat(meaning.getExplanation()).isEqualTo("新聞 (しんぶん): Báo");

        // 文脈規定: từ được khoét khỏi câu, 3 đáp án nhiễu là các danh từ khác cùng cấp độ.
        ExamQuestion context = question(saved, 1L, CONTEXT);
        assertThat(context.getSentence()).isEqualTo("毎朝（　　）を読みます。");
        assertThat(context.getHighlight()).isNull();
        assertThat(correctAnswer(context)).isEqualTo("新聞");
        assertThat(List.of(context.getOptionA(), context.getOptionB(), context.getOptionC(), context.getOptionD()))
                .containsExactlyInAnyOrder("新聞", "学校", "先生", "病院");
        assertThat(context.getSkill()).isEqualTo(MEANING);
    }

    @Test
    void generate_shouldAskContextOnlyForNounsAndVerbs_inSentencesWithEnoughContext_withoutNearSynonymChoices() {
        Kanji work = word(11L, "仕事", "しごと", "父は毎日遅くまで仕事をしています。", "Công việc, việc làm");
        // Gần nghĩa với 仕事: điền vào câu trên cũng đúng.
        Kanji task = word(12L, "作業", "さぎょう", null, "Công việc (tay chân), thao tác");
        Kanji company = word(13L, "会社", "かいしゃ", null, "Công ty");
        Kanji bank = word(14L, "銀行", "ぎんこう", null, "Ngân hàng");
        Kanji film = word(15L, "映画", "えいが", null, "Phim");
        // Câu ngắn: 「（　　）へ行く。」 hợp với gần như mọi nơi chốn.
        Kanji school = word(16L, "学校", "がっこう", "学校へ行く。", "Trường học");
        // Tính từ: đủ 3 tính từ khác làm đáp án nhiễu nhưng vẫn không hỏi.
        Kanji small = word(17L, "小さい", "ちいさい", "この箱は小さいので、本が入りません。", "Nhỏ, bé");
        Kanji big = word(18L, "大きい", "おおきい", null, "To, lớn");
        Kanji fresh = word(19L, "新しい", "あたらしい", null, "Mới");
        Kanji high = word(20L, "高い", "たかい", null, "Cao, đắt");
        when(kanjiRepository.findAllByTagNamePrefix("N5-%"))
                .thenReturn(List.of(work, task, company, bank, film, school, small, big, fresh, high));
        when(questionRepository.generatedQuestionWords("N5")).thenReturn(List.of());

        generator.generate("N5");

        List<ExamQuestion> context = savedQuestions().stream()
                .filter(question -> CONTEXT.equals(question.getQuestionType()))
                .toList();
        // Chỉ 仕事 được hỏi; đáp án nhiễu là danh từ khác (学校 vẫn làm đáp án nhiễu được), trừ 作業.
        assertThat(context).singleElement().satisfies(question -> {
            assertThat(question.getSentence()).isEqualTo("父は毎日遅くまで（　　）をしています。");
            assertThat(List.of(question.getOptionA(), question.getOptionB(), question.getOptionC(),
                    question.getOptionD()))
                    .contains("仕事")
                    .doesNotContain("作業")
                    .isSubsetOf("仕事", "会社", "銀行", "映画", "学校");
        });
    }

    @Test
    void generate_shouldAskN5KatakanaWordsInKatakana_fromTheirSentenceWrittenInHiragana() {
        Kanji guitar = word(31L, "ギター", null, "兄は部屋でギターを弾いています。", "Đàn ghi-ta");
        // ペン nằm trong ボールペン: không gạch chân riêng được. カメラ không có câu ví dụ.
        Kanji pen = word(32L, "ペン", null, "ボールペンで書きます。", "Bút");
        Kanji camera = word(33L, "カメラ", null, null, "Máy ảnh");
        Kanji bus = word(34L, "バス", null, "バスに乗ります。", "Xe buýt");
        when(kanjiRepository.findAllByTagNamePrefix("N5-%")).thenReturn(List.of(guitar, pen, camera, bus));
        // バス đã có câu 表記.
        when(questionRepository.generatedQuestionWords("N5")).thenReturn(List.of(generated(34L, ORTHOGRAPHY)));

        generator.generate("N5");

        List<ExamQuestion> writing = savedQuestions().stream()
                .filter(question -> ORTHOGRAPHY.equals(question.getQuestionType()))
                .toList();
        assertThat(writing).singleElement().satisfies(question -> {
            assertThat(question.getKanjiIds()).containsExactly(31L);
            assertThat(question.getQuestionText()).isEqualTo(ExamQuestionGenerator.KATAKANA_ORTHOGRAPHY_TEXT);
            assertThat(question.getSentence()).isEqualTo("兄は部屋でぎたーを弾いています。");
            assertThat(question.getHighlight()).isEqualTo("ぎたー");
            assertThat(correctAnswer(question)).isEqualTo("ギター");
            List<String> options = List.of(question.getOptionA(), question.getOptionB(), question.getOptionC(),
                    question.getOptionD());
            assertThat(options).doesNotHaveDuplicates().filteredOn(option -> !option.equals("ギター"))
                    .hasSize(3).isSubsetOf(KatakanaSpelling.allMisspellings("ギター"));
            assertThat(question.getSkill()).isEqualTo(READING_TO_KANJI);
            assertThat(question.getSource()).isEqualTo(ExamQuestionSource.GENERATED);
        });
    }

    @Test
    void generate_shouldAskKatakanaWritingOnlyInN5() {
        Kanji guitar = word(31L, "ギター", null, "兄は部屋でギターを弾いています。", "Đàn ghi-ta");
        when(kanjiRepository.findAllByTagNamePrefix("N4-%")).thenReturn(List.of(guitar, newspaper, school, teacher));
        when(questionRepository.generatedQuestionWords("N4")).thenReturn(List.of());

        generator.generate("N4");

        assertThat(savedQuestions()).noneSatisfy(question -> {
            assertThat(question.getKanjiIds()).containsExactly(31L);
            assertThat(question.getQuestionType()).isEqualTo(ORTHOGRAPHY);
        });
    }

    @Test
    void sharesMeaning_shouldCompareMeaningPhrases_ignoringNotesInBrackets() {
        assertThat(ExamQuestionGenerator.sharesMeaning("Nhỏ, bé", "Nhỏ, chi tiết")).isTrue();
        assertThat(ExamQuestionGenerator.sharesMeaning("Công việc, việc làm", "Công việc (tay chân), thao tác")).isTrue();
        // Chỉ chung ghi chú "(tha động từ)", hoặc chung một tiếng trong cụm: không tính.
        assertThat(ExamQuestionGenerator.sharesMeaning("Tìm thấy (tha động từ)", "Làm chuyển động (tha động từ)"))
                .isFalse();
        assertThat(ExamQuestionGenerator.sharesMeaning("Phòng", "Phòng học")).isFalse();
        assertThat(ExamQuestionGenerator.sharesMeaning(null, "Phòng")).isFalse();
    }

    @Test
    void generate_shouldSkipQuestionsThatCannotHaveFourChoices() {
        // Cả cấp độ chỉ có 2 từ: không đủ đáp án nhiễu cho câu hỏi nghĩa.
        when(kanjiRepository.findAllByTagNamePrefix("N3-%")).thenReturn(List.of(yes, hospital));
        when(questionRepository.generatedQuestionWords("N3")).thenReturn(List.of());

        assertThat(generator.generate("N3").created()).isZero();
    }

    private List<ExamQuestion> savedQuestions() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ExamQuestion>> captor = ArgumentCaptor.forClass(List.class);
        verify(questionRepository, atLeastOnce()).saveAll(captor.capture());
        List<ExamQuestion> all = new ArrayList<>();
        captor.getAllValues().forEach(all::addAll);
        return all;
    }

    private static ExamQuestion question(List<ExamQuestion> questions, Long kanjiId, String kind) {
        return questions.stream()
                .filter(question -> question.getKanjiIds().equals(Set.of(kanjiId)) && kind.equals(kind(question)))
                .findFirst()
                .orElseThrow();
    }

    /** Dạng câu JLPT, hoặc kỹ năng với câu hỏi nghĩa (chỉ dùng cho thi nhanh). */
    private static String kind(ExamQuestion question) {
        return question.getQuestionType() != null ? question.getQuestionType() : String.valueOf(question.getSkill());
    }

    private static String correctAnswer(ExamQuestion question) {
        return switch (question.getCorrectOption()) {
            case "A" -> question.getOptionA();
            case "B" -> question.getOptionB();
            case "C" -> question.getOptionC();
            default -> question.getOptionD();
        };
    }

    private static GeneratedQuestionWord generated(Long kanjiId, String kind) {
        return new GeneratedQuestionWord() {
            @Override
            public Long getKanjiId() {
                return kanjiId;
            }

            @Override
            public String getKind() {
                return kind;
            }
        };
    }

    private static Kanji word(Long id, String character, String reading, String sentence, String meaning) {
        return Kanji.builder().id(id).character(character).reading(reading).exampleSentence(sentence).meaning(meaning)
                .hanViet("").jlptLevel("N4").strokeCount(5).build();
    }
}
