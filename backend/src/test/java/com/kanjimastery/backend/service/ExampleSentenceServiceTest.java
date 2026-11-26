package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.repository.KanjiRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** AI viết câu ví dụ theo lô cho từ còn thiếu câu; không gọi Gemini thật. */
@ExtendWith(MockitoExtension.class)
class ExampleSentenceServiceTest {

    @Mock
    private KanjiRepository kanjiRepository;
    @Mock
    private SentenceGenerationService sentenceGenerationService;
    @Mock
    private RateLimiterService rateLimiter;

    @InjectMocks
    private ExampleSentenceService service;

    @Test
    void fillMissing_shouldSendAtMostTheRequestsAllowed_fortyWordsEach() {
        List<Kanji> words = words(100);
        when(sentenceGenerationService.isEnabled()).thenReturn(true);
        when(kanjiRepository.findWithoutExampleSentence(2 * ExampleSentenceService.BATCH_SIZE * 2)).thenReturn(words);
        when(rateLimiter.counts(anyList())).thenReturn(Collections.nCopies(100, 0L));
        when(sentenceGenerationService.generateSentences(anyList())).thenAnswer(invocation -> {
            List<Kanji> batch = invocation.getArgument(0);
            return Optional.of(Map.of(batch.get(0).getId(), "文。"));
        });
        when(kanjiRepository.saveExampleSentenceIfAbsent(anyLong(), anyString())).thenReturn(1);
        when(kanjiRepository.countWithoutExampleSentence()).thenReturn(98L);

        ExampleSentenceService.Result result = service.fillMissing(2);

        assertThat(result).isEqualTo(new ExampleSentenceService.Result(2, 2, 98, null));
        verify(sentenceGenerationService, times(2)).generateSentences(anyList());
        // Từ Gemini trả lời mà không có câu dùng được: tính một lần thất bại để không gửi lại mãi.
        verify(rateLimiter, times(2 * ExampleSentenceService.BATCH_SIZE - 2)).increment(anyString(), any());
    }

    @Test
    void fillMissing_shouldSkipWordsThatFailedTwice_andStopWhenGeminiCannotAnswer() {
        List<Kanji> words = words(3);
        when(sentenceGenerationService.isEnabled()).thenReturn(true);
        when(kanjiRepository.findWithoutExampleSentence(anyInt())).thenReturn(words);
        when(rateLimiter.counts(anyList())).thenReturn(List.of(0L, (long) ExampleSentenceService.MAX_FAILURES, 0L));
        when(sentenceGenerationService.generateSentences(anyList())).thenReturn(Optional.empty());

        ExampleSentenceService.Result result = service.fillMissing(5);

        verify(sentenceGenerationService).generateSentences(List.of(words.get(0), words.get(2)));
        assertThat(result.requests()).isZero();
        assertThat(result.stopped()).contains("Gemini");
        verify(kanjiRepository, never()).saveExampleSentenceIfAbsent(anyLong(), anyString());
    }

    @Test
    void fillMissing_shouldDoNothing_whenAiIsNotConfigured() {
        when(sentenceGenerationService.isEnabled()).thenReturn(false);
        when(kanjiRepository.countWithoutExampleSentence()).thenReturn(7L);

        ExampleSentenceService.Result result = service.fillMissing(20);

        assertThat(result.requests()).isZero();
        assertThat(result.stillMissing()).isEqualTo(7);
        verify(kanjiRepository, never()).findWithoutExampleSentence(anyInt());
        verify(sentenceGenerationService, never()).generateSentences(anyList());
    }

    private static List<Kanji> words(int count) {
        return LongStream.rangeClosed(1, count)
                .mapToObj(id -> Kanji.builder().id(id).character("語" + id).meaning("nghĩa").hanViet("")
                        .strokeCount(1).jlptLevel(JlptLevel.N2).build())
                .toList();
    }
}
