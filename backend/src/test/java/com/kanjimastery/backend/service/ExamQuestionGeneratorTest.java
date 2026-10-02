package com.kanjimastery.backend.service;

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

import static com.kanjimastery.backend.model.QuizDirection.KANJI_TO_READING;
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
    private final Kanji school = word(2L, "学校", "がっこう", "学校へ行きます。", "Trường học");
    private final Kanji teacher = word(3L, "先生", "せんせい", "先生に聞きます。", "Giáo viên");
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
    void generate_shouldAskReadingAndWritingInJlptStyle_andMeaningForEveryWord_skippingWhatExists() {
        when(kanjiRepository.findAllByTagNamePrefix("N4-%"))
                .thenReturn(List.of(newspaper, school, teacher, see, yes, hospital));
        when(questionRepository.generatedQuestionWords("N4")).thenReturn(List.of(generated(2L, MEANING)));

        ExamQuestionGenerator.Result result = generator.generate("n4");

        List<ExamQuestion> saved = savedQuestions();
        // 新聞, 先生: đọc + viết + nghĩa; 学校 đã có câu nghĩa; 見る: cách đọc "み(る)" không thay vào câu được nên
        // không hỏi viết; はい không có chữ Hán, 病院 không có câu ví dụ: chỉ hỏi nghĩa.
        assertThat(saved).extracting(question -> question.getKanjiIds().iterator().next(), ExamQuestion::getSkill)
                .containsExactlyInAnyOrder(
                        tuple(1L, KANJI_TO_READING), tuple(1L, READING_TO_KANJI), tuple(1L, MEANING),
                        tuple(2L, KANJI_TO_READING), tuple(2L, READING_TO_KANJI),
                        tuple(3L, KANJI_TO_READING), tuple(3L, READING_TO_KANJI), tuple(3L, MEANING),
                        tuple(4L, KANJI_TO_READING), tuple(4L, MEANING),
                        tuple(5L, MEANING), tuple(6L, MEANING));
        assertThat(result).isEqualTo(new ExamQuestionGenerator.Result("N4", 6, 12));
        assertThat(saved).allSatisfy(question -> {
            assertThat(question.getJlptLevel()).isEqualTo("N4");
            assertThat(question.getSource()).isEqualTo(ExamQuestionSource.GENERATED);
            assertThat(List.of(question.getOptionA(), question.getOptionB(), question.getOptionC(),
                    question.getOptionD())).doesNotHaveDuplicates();
        });

        ExamQuestion reading = question(saved, 1L, KANJI_TO_READING);
        assertThat(reading.getSentence()).isEqualTo("毎朝新聞を読みます。");
        assertThat(reading.getHighlight()).isEqualTo("新聞");
        assertThat(correctAnswer(reading)).isEqualTo("しんぶん");

        ExamQuestion writing = question(saved, 1L, READING_TO_KANJI);
        assertThat(writing.getSentence()).isEqualTo("毎朝しんぶんを読みます。");
        assertThat(writing.getHighlight()).isEqualTo("しんぶん");
        assertThat(correctAnswer(writing)).isEqualTo("新聞");

        ExamQuestion meaning = question(saved, 1L, MEANING);
        assertThat(meaning.getQuestionText()).isEqualTo("Từ 「新聞」 (しんぶん) có nghĩa là gì?");
        // Câu hỏi nghĩa vẫn kèm câu ví dụ làm ngữ cảnh, như trắc nghiệm.
        assertThat(meaning.getSentence()).isEqualTo("毎朝新聞を読みます。");
        assertThat(meaning.getHighlight()).isEqualTo("新聞");
        assertThat(question(saved, 6L, MEANING).getSentence()).isNull();
        assertThat(correctAnswer(meaning)).isEqualTo("Báo");
        assertThat(meaning.getExplanation()).isEqualTo("新聞 (しんぶん): Báo");
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

    private static ExamQuestion question(List<ExamQuestion> questions, Long kanjiId, String skill) {
        return questions.stream()
                .filter(question -> question.getKanjiIds().equals(Set.of(kanjiId)) && skill.equals(question.getSkill()))
                .findFirst()
                .orElseThrow();
    }

    private static String correctAnswer(ExamQuestion question) {
        return switch (question.getCorrectOption()) {
            case "A" -> question.getOptionA();
            case "B" -> question.getOptionB();
            case "C" -> question.getOptionC();
            default -> question.getOptionD();
        };
    }

    private static GeneratedQuestionWord generated(Long kanjiId, String skill) {
        return new GeneratedQuestionWord() {
            @Override
            public Long getKanjiId() {
                return kanjiId;
            }

            @Override
            public String getSkill() {
                return skill;
            }
        };
    }

    private static Kanji word(Long id, String character, String reading, String sentence, String meaning) {
        return Kanji.builder().id(id).character(character).reading(reading).exampleSentence(sentence).meaning(meaning)
                .hanViet("").jlptLevel("N4").strokeCount(5).build();
    }
}
