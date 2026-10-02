package com.kanjimastery.backend.service;

import com.kanjimastery.backend.config.RateLimitProperties;
import com.kanjimastery.backend.dto.QuizQuestionResponse;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.exception.TooManyRequestsException;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.model.User;
import com.kanjimastery.backend.model.UserKanjiSrs;
import com.kanjimastery.backend.repository.KanjiRepository;
import com.kanjimastery.backend.service.LearnerHistoryService.PastMistake;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

    private static final Long TAG_ID = 5L;
    private static final Long USER_ID = 7L;
    private static final String ADAPTIVE = QuizService.MODE_ADAPTIVE;

    @Mock
    private KanjiRepository kanjiRepository;
    @Mock
    private SentenceGenerationService sentenceGenerationService;
    @Mock
    private RateLimiterService rateLimiter;
    @Mock
    private UserService userService;
    @Mock
    private LearnerHistoryService learnerHistoryService;

    private final QuizDistractorGenerator distractors = new QuizDistractorGenerator();
    private QuizService quizService;

    // 乾く đã thất bại 2 lần gần đây; 渇く chưa có câu; 固い đã có câu ví dụ.
    private final Kanji failedWord = word(1L, "乾く", "かわく", null);
    private final Kanji missingWord = word(2L, "渇く", "かわく", null);
    private final Kanji readyWord = word(3L, "固い", "かたい", "この肉は固い。");

    @BeforeEach
    void setUp() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.getQuiz().setLimit(30);
        properties.getQuiz().setWindowSeconds(60);
        quizService = new QuizService(kanjiRepository, sentenceGenerationService, rateLimiter, properties, distractors,
                userService, learnerHistoryService);
        lenient().when(userService.getByUsername("taro")).thenReturn(User.builder().id(USER_ID).build());
        lenient().when(learnerHistoryService.load(eq(USER_ID), anyCollection()))
                .thenAnswer(invocation -> LearnerHistory.empty(LocalDateTime.now()));
        lenient().when(learnerHistoryService.pastMistakes(eq(USER_ID), anyCollection())).thenReturn(Map.of());
    }

    @AfterEach
    void tearDown() {
        quizService.shutdownExecutor();
    }

    @Test
    void generate_shouldRejectRequest_whenUserExceedsQuizLimit() {
        when(rateLimiter.tryAcquire("ratelimit:quiz:taro", 30, Duration.ofSeconds(60))).thenReturn(false);

        assertThatThrownBy(() -> quizService.generate("taro", TAG_ID, null, 10, ADAPTIVE))
                .isInstanceOf(TooManyRequestsException.class);
        verifyNoInteractions(kanjiRepository, sentenceGenerationService);
    }

    @Test
    void generate_shouldRejectUnknownMode() {
        assertThatThrownBy(() -> quizService.generate("taro", TAG_ID, null, 10, "hard"))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(rateLimiter, kanjiRepository);
    }

    @Test
    void generate_shouldAskMostlyWeakWords_inAdaptiveMode() {
        givenQuizAllowed();
        List<Kanji> lesson = IntStream.rangeClosed(1, 30)
                .mapToObj(i -> word((long) i, "語" + i, "ご" + i, "語" + i + "を使う。"))
                .toList();
        when(kanjiRepository.findAllByFilters(null, TAG_ID)).thenReturn(lesson);
        // 10 từ đầu đã đến hạn ôn, 20 từ còn lại chưa gặp: 10 câu = 6 từ yếu + 3 từ mới + 1 câu bù.
        HashMap<Long, UserKanjiSrs> dueCards = new HashMap<>();
        for (long id = 1; id <= 10; id++) {
            dueCards.put(id, UserKanjiSrs.builder().kanjiId(id).repetitionCount(2).easinessFactor(new BigDecimal("2.50"))
                    .reviewIntervalDays(6).nextReviewAt(LocalDateTime.now().minusDays(1)).build());
        }
        when(learnerHistoryService.load(eq(USER_ID), anyCollection()))
                .thenReturn(new LearnerHistory(LocalDateTime.now(), dueCards, Map.of(), Map.of()));

        List<QuizQuestionResponse> questions = quizService.generate("taro", TAG_ID, null, 10, ADAPTIVE);

        assertThat(questions).hasSize(10);
        assertThat(questions).filteredOn(question -> question.getKanjiId() <= 10).hasSizeBetween(6, 7);
    }

    @Test
    void generate_shouldNotReadLearningHistory_inRandomMode() {
        givenQuizAllowed();
        when(kanjiRepository.findAllByFilters(null, TAG_ID)).thenReturn(List.of(readyWord));

        assertThat(quizService.generate("taro", TAG_ID, null, 10, QuizService.MODE_RANDOM)).hasSize(1);
        verify(learnerHistoryService, never()).load(anyLong(), anyCollection());
    }

    @Test
    void generate_shouldNotSendWordsThatFailedRecently() {
        givenLessonWithGeminiAnswer(Optional.of(Map.of()));

        quizService.generate("taro", TAG_ID, null, 10, ADAPTIVE);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Kanji>> batch = ArgumentCaptor.forClass(List.class);
        verify(sentenceGenerationService).generateSentences(batch.capture());
        assertThat(batch.getValue()).extracting(Kanji::getId).containsExactly(2L);
    }

    @Test
    void generate_shouldRecordFailure_whenGeminiAnsweredWithoutAUsableSentence() {
        givenLessonWithGeminiAnswer(Optional.of(Map.of()));

        quizService.generate("taro", TAG_ID, null, 10, ADAPTIVE);

        verify(rateLimiter).increment("sentence:failures:2", Duration.ofHours(24));
    }

    @Test
    void generate_shouldNotRecordFailure_whenGeminiDidNotAnswer() {
        givenLessonWithGeminiAnswer(Optional.empty());

        quizService.generate("taro", TAG_ID, null, 10, ADAPTIVE);

        verify(rateLimiter, never()).increment(startsWith("sentence:failures:"), any());
    }

    @Test
    void generate_shouldSaveNewSentence_andNotCountItAsFailure() {
        givenLessonWithGeminiAnswer(Optional.of(Map.of(2L, "のどが渇く。")));

        quizService.generate("taro", TAG_ID, null, 10, ADAPTIVE);

        verify(kanjiRepository).saveExampleSentenceIfAbsent(2L, "のどが渇く。");
        verify(rateLimiter, never()).increment(startsWith("sentence:failures:"), any());
    }

    @Test
    void generate_shouldCapQuestionCount_forAWholeLevel() {
        givenQuizAllowed();
        List<Kanji> level = IntStream.rangeClosed(1, 80)
                .mapToObj(i -> word((long) i, "語" + i, "ご" + i, null))
                .toList();
        when(kanjiRepository.findAllByTagNamePrefix("N3-%")).thenReturn(level);

        assertThat(quizService.generate("taro", null, "n3", 1000, ADAPTIVE)).hasSize(50);
    }

    @Test
    void generate_shouldOfferLookAlikeSpellings_butNotAnExistingWordReadTheSameWay() {
        givenQuizAllowed();
        when(kanjiRepository.findAllByFilters(null, TAG_ID)).thenReturn(List.of(word(10L, "検査", "けんさ", null)));
        // Giả sử kho từ vựng có một từ viết 険査 cũng đọc けんさ: đáp án đó cũng "đúng", không được đưa vào.
        when(kanjiRepository.findAllByCharacterIn(anyCollection())).thenReturn(List.of(word(99L, "険査", "けんさ", null)));

        QuizQuestionResponse question = firstQuestionAsking("READING_TO_KANJI");

        assertThat(question.getChoices()).hasSize(4).contains("検査").doesNotContain("険査");
        assertThat(question.getChoices()).filteredOn(choice -> !choice.equals("検査"))
                .allSatisfy(choice -> assertThat(distractors.lookAlikeSpellings("検査")).contains(choice));
    }

    @Test
    void generate_shouldSkipLookAlikeSpelling_whenAnyRowOfThatSpellingReadsTheSame() {
        givenQuizAllowed();
        when(kanjiRepository.findAllByFilters(null, TAG_ID)).thenReturn(List.of(word(10L, "検査", "けんさ", null)));
        // 険査 có hai dòng: dòng đọc khác đứng trước không được che mất dòng cũng đọc けんさ.
        when(kanjiRepository.findAllByCharacterIn(anyCollection()))
                .thenReturn(List.of(word(98L, "険査", "けんせい", null), word(99L, "険査", "けんさ", null)));

        QuizQuestionResponse question = firstQuestionAsking("READING_TO_KANJI");

        assertThat(question.getChoices()).contains("検査").doesNotContain("険査");
    }

    @Test
    void generate_shouldNotOfferTheMeaningOfAnotherRowOfTheSameWord() {
        givenQuizAllowed();
        Kanji light = kanaWord(40L, "つく", "Sáng [điện ~]");
        Kanji stick = kanaWord(41L, "つく", "Dính");
        when(kanjiRepository.findAllByFilters(null, TAG_ID)).thenReturn(List.of(light, stick,
                kanaWord(42L, "はる", "Dán"), kanaWord(43L, "すく", "Vắng"), kanaWord(44L, "やむ", "Tạnh")));

        QuizQuestionResponse question = firstQuestionAsking("MEANING", "つく");

        assertThat(question.getChoices()).hasSize(4).contains("Dán", "Vắng", "Tạnh");
        assertThat(question.getChoices()).containsOnlyOnce(question.getMeaning());
        assertThat(question.getChoices()).filteredOn(choice -> choice.equals("Sáng [điện ~]") || choice.equals("Dính")).hasSize(1);
    }

    @Test
    void generate_shouldFillMissingLookAlikes_withWordsSharingTheOkurigana_butNotHomophones() {
        givenQuizAllowed();
        when(kanjiRepository.findAllByFilters(null, TAG_ID)).thenReturn(List.of(
                word(30L, "厚い", "あつい", null), word(31L, "暑い", "あつい", null),
                word(32L, "高い", "たかい", null), word(33L, "若い", "わかい", null),
                word(34L, "交番", "こうばん", null), word(35L, "調査", "ちょうさ", null)));

        QuizQuestionResponse question = firstQuestionAsking("READING_TO_KANJI", "厚い");

        // 厚 chỉ có một chữ trông giống (原) - hai đáp án còn lại là tính từ 〜い, không phải danh từ hay 暑い cùng âm.
        assertThat(question.getChoices()).containsExactlyInAnyOrder("厚い", "原い", "高い", "若い");
    }

    @Test
    void generate_shouldOfferSoundTrapReadings_beforeReadingsOfOtherWords() {
        givenQuizAllowed();
        when(kanjiRepository.findAllByFilters(null, TAG_ID))
                .thenReturn(List.of(word(20L, "周辺", "しゅうへん", null), word(21L, "書類", "しょるい", null)));

        QuizQuestionResponse question = firstQuestionAsking("KANJI_TO_READING", "周辺");

        assertThat(question.getChoices()).hasSize(4).contains("しゅうへん").doesNotContain("しょるい");
        assertThat(question.getChoices()).filteredOn(choice -> !choice.equals("しゅうへん"))
                .allSatisfy(choice -> assertThat(distractors.trapReadings("しゅうへん", "周辺")).contains(choice));
    }

    @Test
    void generate_shouldOfferTheLearnersOwnPastMistake_beforeSoundTraps() {
        givenQuizAllowed();
        when(kanjiRepository.findAllByFilters(null, TAG_ID))
                .thenReturn(List.of(word(20L, "周辺", "しゅうへん", null), word(21L, "書類", "しょるい", null)));
        givenPastMistakes(20L, "KANJI_TO_READING", new PastMistake("しょるい", 3));

        QuizQuestionResponse question = firstQuestionAsking("KANJI_TO_READING", "周辺");

        // Không có lịch sử thì しょるい bị cách đọc bẫy đẩy ra (xem test trên); người học từng chọn nó 3 lần nên phải có.
        assertThat(question.getChoices()).hasSize(4).contains("しゅうへん", "しょるい");
        assertThat(question.getPersonalTrap()).isEqualTo("しょるい");
        assertThat(question.getPersonalTrapCount()).isEqualTo(3);
        assertThat(question.getPersonalTrapReading()).isNull();
    }

    @Test
    void generate_shouldShowReadingAndMeaning_whenThePastMistakeIsARealWord() {
        givenQuizAllowed();
        when(kanjiRepository.findAllByFilters(null, TAG_ID)).thenReturn(List.of(word(60L, "待つ", "まつ", null)));
        givenPastMistakes(60L, "READING_TO_KANJI", new PastMistake("持つ", 2));
        when(kanjiRepository.findAllByCharacterIn(anyCollection())).thenReturn(List.of(word(61L, "持つ", "もつ", null)));

        QuizQuestionResponse question = firstQuestionAsking("READING_TO_KANJI", "待つ");

        assertThat(question.getChoices()).contains("待つ", "持つ");
        assertThat(question.getPersonalTrap()).isEqualTo("持つ");
        assertThat(question.getPersonalTrapReading()).isEqualTo("もつ");
        assertThat(question.getPersonalTrapMeaning()).isEqualTo("nghĩa của 持つ");
    }

    @Test
    void generate_shouldDropAPastMistake_thatIsAlsoCorrect() {
        givenQuizAllowed();
        when(kanjiRepository.findAllByFilters(null, TAG_ID)).thenReturn(List.of(word(70L, "会う", "あう", null)));
        // Người học từng chọn 合う - nhưng kho có từ 合う cũng đọc あう, nên đáp án đó cũng "đúng".
        givenPastMistakes(70L, "READING_TO_KANJI", new PastMistake("合う", 4));
        when(kanjiRepository.findAllByCharacterIn(anyCollection())).thenReturn(List.of(word(71L, "合う", "あう", null)));

        QuizQuestionResponse question = firstQuestionAsking("READING_TO_KANJI", "会う");

        assertThat(question.getChoices()).contains("会う").doesNotContain("合う");
        assertThat(question.getPersonalTrap()).isNull();
    }

    @Test
    void generate_shouldDropAPastMistake_thatIsTheReadingOfAnotherRowOfTheSameWord() {
        givenQuizAllowed();
        Kanji aku = word(50L, "開く", "あく", null);
        when(kanjiRepository.findAllByFilters(null, TAG_ID)).thenReturn(List.of(aku));
        givenPastMistakes(50L, "KANJI_TO_READING", new PastMistake("ひらく", 2));
        when(kanjiRepository.findAllByCharacterIn(anyCollection())).thenReturn(List.of(aku, word(51L, "開く", "ひらく", null)));

        QuizQuestionResponse question = firstQuestionAsking("KANJI_TO_READING", "開く");

        assertThat(question.getChoices()).contains("あく").doesNotContain("ひらく");
        assertThat(question.getPersonalTrap()).isNull();
    }

    private void givenPastMistakes(Long kanjiId, String direction, PastMistake... mistakes) {
        when(learnerHistoryService.pastMistakes(eq(USER_ID), anyCollection()))
                .thenReturn(Map.of(kanjiId, Map.of(direction, List.of(mistakes))));
    }

    /** Hướng hỏi được chọn ngẫu nhiên - tạo lại tới khi gặp câu cần kiểm tra (xác suất trượt 2^-100). */
    private QuizQuestionResponse firstQuestionAsking(String direction, String... character) {
        for (int attempt = 0; attempt < 100; attempt++) {
            for (QuizQuestionResponse question : quizService.generate("taro", TAG_ID, null, 10, ADAPTIVE)) {
                if (question.getDirection().equals(direction)
                        && (character.length == 0 || question.getCharacter().equals(character[0]))) {
                    return question;
                }
            }
        }
        throw new AssertionError("Không sinh được câu hỏi " + direction);
    }

    private void givenQuizAllowed() {
        when(rateLimiter.tryAcquire("ratelimit:quiz:taro", 30, Duration.ofSeconds(60))).thenReturn(true);
    }

    private void givenLessonWithGeminiAnswer(Optional<Map<Long, String>> answer) {
        when(rateLimiter.tryAcquire("ratelimit:quiz:taro", 30, Duration.ofSeconds(60))).thenReturn(true);
        when(kanjiRepository.findAllByFilters(null, TAG_ID)).thenReturn(List.of(failedWord, missingWord, readyWord));
        when(sentenceGenerationService.isEnabled()).thenReturn(true);
        when(rateLimiter.counts(anyList())).thenAnswer(invocation -> invocation.<List<String>>getArgument(0).stream()
                .map(key -> key.equals("sentence:failures:1") ? 2L : 0L)
                .toList());
        when(sentenceGenerationService.generateSentences(anyList())).thenReturn(answer);
    }

    private static Kanji word(Long id, String character, String reading, String sentence) {
        return Kanji.builder()
                .id(id)
                .character(character)
                .reading(reading)
                .hanViet("")
                .meaning("nghĩa của " + character)
                .jlptLevel("N3")
                .strokeCount(10)
                .exampleSentence(sentence)
                .build();
    }

    /** Từ chỉ có kana: không có cách đọc riêng nên luôn được hỏi nghĩa. */
    private static Kanji kanaWord(Long id, String character, String meaning) {
        return Kanji.builder()
                .id(id)
                .character(character)
                .hanViet("")
                .meaning(meaning)
                .jlptLevel("N4")
                .strokeCount(0)
                .build();
    }
}
