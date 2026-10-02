package com.kanjimastery.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanjimastery.backend.config.RateLimitProperties;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.exception.TooManyRequestsException;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.repository.KanjiRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MnemonicServiceTest {

    @Mock
    private GeminiClient geminiClient;
    @Mock
    private KanjiRepository kanjiRepository;
    @Mock
    private RateLimiterService rateLimiter;

    private MnemonicService service;

    private final Kanji open = Kanji.builder().id(5L).character("開く").reading("あく").hanViet("KHAI").meaning("Mở").build();

    @BeforeEach
    void setUp() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.getMnemonic().setLimit(20);
        properties.getMnemonic().setWindowSeconds(3600);
        service = new MnemonicService(geminiClient, kanjiRepository, new ObjectMapper(), rateLimiter, properties);
    }

    @Test
    void getOrGenerate_shouldReturnTheStoredMnemonic_withoutCallingAiOrCountingTheLimit() {
        open.setMnemonic("KHAI trương là mở cửa hàng.");
        when(kanjiRepository.findById(5L)).thenReturn(Optional.of(open));

        assertThat(service.getOrGenerate("taro", 5L).getMnemonic()).isEqualTo("KHAI trương là mở cửa hàng.");
        verifyNoInteractions(geminiClient, rateLimiter);
    }

    @Test
    void getOrGenerate_shouldRefuse_whenAiIsNotConfigured() {
        when(kanjiRepository.findById(5L)).thenReturn(Optional.of(open));
        when(geminiClient.isEnabled()).thenReturn(false);

        assertThatThrownBy(() -> service.getOrGenerate("taro", 5L)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void getOrGenerate_shouldNotCallAi_whenTheLearnerAskedTooOften() {
        givenAiEnabledFor(false);

        assertThatThrownBy(() -> service.getOrGenerate("taro", 5L)).isInstanceOf(TooManyRequestsException.class);
        verify(geminiClient, never()).generateJson(anyString());
    }

    @Test
    void getOrGenerate_shouldAskAiWithTheHanVietReading_andStoreTheAnswerForEveryone() {
        givenAiEnabledFor(true);
        when(geminiClient.generateJson(anyString()))
                .thenReturn(Optional.of("```json\n{\"mnemonic\": \"KHAI trương là MỞ cửa hàng → 開く = mở.\"}\n```"));
        when(kanjiRepository.saveMnemonicIfAbsent(5L, "KHAI trương là MỞ cửa hàng → 開く = mở.")).thenReturn(1);

        assertThat(service.getOrGenerate("taro", 5L).getMnemonic()).isEqualTo("KHAI trương là MỞ cửa hàng → 開く = mở.");

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(geminiClient).generateJson(prompt.capture());
        assertThat(prompt.getValue()).contains("開く", "あく", "KHAI", "Mở");
    }

    @Test
    void getOrGenerate_shouldReturnTheMnemonicSavedFirst_whenSomeoneElseWonTheRace() {
        givenAiEnabledFor(true);
        when(geminiClient.generateJson(anyString())).thenReturn(Optional.of("{\"mnemonic\": \"Bản của tôi\"}"));
        when(kanjiRepository.saveMnemonicIfAbsent(5L, "Bản của tôi")).thenReturn(0);
        when(kanjiRepository.findMnemonicById(5L)).thenReturn(Optional.of("Bản người khác lưu trước"));

        assertThat(service.getOrGenerate("taro", 5L).getMnemonic()).isEqualTo("Bản người khác lưu trước");
    }

    @Test
    void getOrGenerate_shouldNotStoreAnUnusableAnswer() {
        givenAiEnabledFor(true);
        when(geminiClient.generateJson(anyString())).thenReturn(Optional.of("{\"mnemonic\": \"" + "a".repeat(401) + "\"}"));

        assertThatThrownBy(() -> service.getOrGenerate("taro", 5L)).isInstanceOf(TooManyRequestsException.class);
        verify(kanjiRepository, never()).saveMnemonicIfAbsent(anyLong(), anyString());
    }

    private void givenAiEnabledFor(boolean withinLimit) {
        when(kanjiRepository.findById(5L)).thenReturn(Optional.of(open));
        when(geminiClient.isEnabled()).thenReturn(true);
        when(rateLimiter.tryAcquire("ratelimit:mnemonic:taro", 20, Duration.ofSeconds(3600))).thenReturn(withinLimit);
    }
}
