package com.kanjimastery.backend.service;

import com.kanjimastery.backend.dto.RubySegment;
import com.kanjimastery.backend.dto.WordSentenceResponse;
import com.kanjimastery.backend.exception.ResourceNotFoundException;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.KanjiRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Câu dùng từ đang học: câu ví dụ trước, rồi câu thi điền sẵn đáp án; từ đã chia vẫn được đánh dấu. */
class WordSentenceServiceTest {

    // Nạp từ điển Kuromoji một lần cho cả lớp test.
    private static final FuriganaService furigana = new FuriganaService();

    private final KanjiRepository kanjiRepository = mock(KanjiRepository.class);
    private final ExamQuestionRepository questionRepository = mock(ExamQuestionRepository.class);
    private final WordSentenceService service = new WordSentenceService(kanjiRepository, questionRepository, furigana);

    private final Kanji angry = Kanji.builder().id(1L).character("怒る").reading("おこる")
            .exampleSentence("母はとても怒っている。").build();

    @Test
    void fill_shouldTurnEachQuestionIntoAFullSentence() {
        assertThat(WordSentenceService.fill(question(JlptQuestionType.CONTEXT, "雨で試合が（　　）された。", null, "中止")))
                .isEqualTo(new WordSentenceService.Filled("雨で試合が中止された。", "中止"));
        assertThat(WordSentenceService.fill(question(JlptQuestionType.ORTHOGRAPHY, "しあいは ちゅうしだ。", "ちゅうし", "中止")))
                .isEqualTo(new WordSentenceService.Filled("しあいは 中止だ。", "中止"));
        assertThat(WordSentenceService.fill(question(JlptQuestionType.KANJI_READING, "試合は中止だ。", "中止", "ちゅうし")))
                .isEqualTo(new WordSentenceService.Filled("試合は中止だ。", "中止"));
        assertThat(WordSentenceService.fill(question(JlptQuestionType.USAGE, null, null, "雨で試合が中止になった。")))
                .isEqualTo(new WordSentenceService.Filled("雨で試合が中止になった。", null));
        assertThat(WordSentenceService.fill(question(JlptQuestionType.CONTEXT, "（　　）がない文", null, "$1")))
                .as("đáp án có ký tự đặc biệt của regex").extracting(WordSentenceService.Filled::sentence)
                .isEqualTo("$1がない文");
        assertThat(WordSentenceService.fill(question(JlptQuestionType.CONTEXT, "ô trống không có", null, "中止"))).isNull();
    }

    @Test
    void sentences_shouldPutTheExampleFirst_thenExamSentences_markingTheConjugatedWord() {
        when(kanjiRepository.findById(1L)).thenReturn(Optional.of(angry));
        when(questionRepository.findApprovedByWord(eq(1L), anyCollection(), anyInt())).thenReturn(List.of(
                question(JlptQuestionType.PARAPHRASE, "父に怒られた。", "怒られた", "しかられた"),
                question(JlptQuestionType.CONTEXT, "先生が（　　）。", null, "怒った")));

        List<WordSentenceResponse> sentences = service.sentences(1L);

        assertThat(sentences).extracting(WordSentenceResponse::sentence, WordSentenceResponse::source).containsExactly(
                org.assertj.core.groups.Tuple.tuple("母はとても怒っている。", WordSentenceResponse.EXAMPLE),
                org.assertj.core.groups.Tuple.tuple("先生が怒った。", WordSentenceResponse.EXAM),
                org.assertj.core.groups.Tuple.tuple("父に怒られた。", WordSentenceResponse.EXAM));
        assertThat(target(sentences.get(0))).isEqualTo("怒っ");
        assertThat(sentences).allSatisfy(sentence ->
                assertThat(sentence.segments().stream().map(RubySegment::text).collect(Collectors.joining()))
                        .isEqualTo(sentence.sentence()));
    }

    @Test
    void sentences_shouldSkipRepeatedSentences_andStopAtThree() {
        when(kanjiRepository.findById(1L)).thenReturn(Optional.of(angry));
        when(questionRepository.findApprovedByWord(eq(1L), anyCollection(), anyInt())).thenReturn(List.of(
                question(JlptQuestionType.KANJI_READING, "母はとても怒っている。", "怒って", "おこって"),
                question(JlptQuestionType.KANJI_READING, "兄が怒った。", "怒った", "おこった"),
                question(JlptQuestionType.KANJI_READING, "姉が怒った。", "怒った", "おこった"),
                question(JlptQuestionType.KANJI_READING, "弟が怒った。", "怒った", "おこった")));

        assertThat(service.sentences(1L)).extracting(WordSentenceResponse::sentence)
                .containsExactly("母はとても怒っている。", "兄が怒った。", "姉が怒った。");
    }

    @Test
    void sentences_shouldFail_forAnUnknownWord() {
        when(kanjiRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sentences(9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    private static String target(WordSentenceResponse sentence) {
        return sentence.segments().stream().filter(RubySegment::target).map(RubySegment::text)
                .collect(Collectors.joining());
    }

    private static ExamQuestion question(JlptQuestionType type, String sentence, String highlight, String correct) {
        return ExamQuestion.builder().questionType(type).sentence(sentence).highlight(highlight)
                .optionA("x").optionB(correct).optionC("y").optionD("z").correctOption("B").build();
    }
}
