package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.KanjiRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Gắn lại câu từ vựng chưa gắn từ: chạy thử chỉ đếm, ghi thì thêm id từ; từ vẫn thiếu xếp theo số câu hỏi đến. */
@ExtendWith(MockitoExtension.class)
class ExamWordLinkServiceTest {

    @Mock
    private ExamQuestionRepository questionRepository;
    @Mock
    private KanjiRepository kanjiRepository;

    @InjectMocks
    private ExamWordLinkService service;

    private final ExamQuestion delay = reading("延期", "えんき");
    private final ExamQuestion rare = reading("稀有", "けう");
    private final ExamQuestion rareAgain = reading("稀有", "けう");
    private final ExamQuestion other = reading("希少", "きしょう");
    private final Kanji delayWord = Kanji.builder().id(7L).character("延期").reading("えんき").build();

    @Test
    void dryRun_shouldCountTheQuestionsItCanLink_withoutLinkingThem() {
        stubBank();

        ExamWordLinkService.Result result = service.relink(true);

        assertThat(result.dryRun()).isTrue();
        assertThat(result.checked()).isEqualTo(4);
        assertThat(result.linked()).isEqualTo(1);
        assertThat(delay.getKanjiIds()).isEmpty();
    }

    @Test
    void relink_shouldAddTheWordToItsQuestions_andListMissingWordsMostAskedFirst() {
        stubBank();

        ExamWordLinkService.Result result = service.relink(false);

        assertThat(delay.getKanjiIds()).containsExactly(7L);
        assertThat(rare.getKanjiIds()).isEmpty();
        assertThat(result.missingWords()).isEqualTo(2);
        assertThat(result.missing()).containsExactly(new ExamWordLinkService.MissingWord("稀有", 2),
                new ExamWordLinkService.MissingWord("希少", 1));
    }

    @Test
    void conjugatedWords_shouldBeLinkedToTheirDictionaryForm_andListedForReview() {
        ExamQuestion angry = reading("怒って", "おこって");
        ExamQuestion angryAgain = reading("怒って", "おこって");
        Kanji angryWord = Kanji.builder().id(9L).character("怒る").reading("おこる").build();
        when(questionRepository.findWithoutWords(anyCollection())).thenReturn(List.of(delay, angry, angryAgain));
        when(kanjiRepository.findAllByCharacterIn(anyCollection())).thenReturn(List.of(delayWord, angryWord));

        ExamWordLinkService.Result result = service.relink(true);

        assertThat(result.linked()).isEqualTo(3);
        assertThat(result.inferredQuestions()).isEqualTo(2);
        assertThat(result.inferred()).containsExactly(new ExamWordLinkService.InferredLink("怒って", "「怒る」", 2));
        assertThat(angry.getKanjiIds()).as("chạy thử").isEmpty();

        service.relink(false);

        assertThat(angry.getKanjiIds()).containsExactly(9L);
        assertThat(delay.getKanjiIds()).containsExactly(7L);
    }

    @Test
    void relink_shouldNotSearchTheBank_whenNoQuestionIsWaiting() {
        when(questionRepository.findWithoutWords(anyCollection())).thenReturn(List.of());

        assertThat(service.relink(false).checked()).isZero();
        verify(kanjiRepository, never()).findAllByCharacterIn(anyCollection());
    }

    private void stubBank() {
        when(questionRepository.findWithoutWords(anyCollection())).thenReturn(List.of(delay, rare, rareAgain, other));
        when(kanjiRepository.findAllByCharacterIn(anyCollection())).thenReturn(List.of(delayWord));
    }

    private static ExamQuestion reading(String word, String reading) {
        return ExamQuestion.builder().questionType(JlptQuestionType.KANJI_READING).highlight(word)
                .optionA(reading).optionB("b").optionC("c").optionD("d").correctOption("A").build();
    }
}
