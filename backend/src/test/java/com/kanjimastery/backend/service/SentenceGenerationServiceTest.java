package com.kanjimastery.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanjimastery.backend.model.Kanji;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SentenceGenerationServiceTest {

    @Mock
    private RateLimiterService rateLimiter;

    /** Model đã được "gọi" - phản hồi giả thay cho mạng thật. */
    private final List<String> calledModels = new ArrayList<>();
    private HttpResponse<String> nextResponse;
    private SentenceGenerationService service;

    private final Kanji dry = Kanji.builder().id(1L).character("乾く").meaning("khô").jlptLevel("N3").build();
    private final Kanji thirsty = Kanji.builder().id(2L).character("渇く").meaning("khát").jlptLevel("N3").build();

    @BeforeEach
    void setUp() {
        service = new SentenceGenerationService(new ObjectMapper(), rateLimiter) {
            @Override
            HttpResponse<String> send(String model, String apiKey, String requestBody) {
                calledModels.add(model);
                return nextResponse;
            }
        };
        ReflectionTestUtils.setField(service, "apiKeysRaw", "test-key");
        ReflectionTestUtils.setField(service, "modelsRaw", "test-model");
        ReflectionTestUtils.setField(service, "dailyRequestLimit", 100);
        service.loadConfig();
    }

    @Test
    void generateSentences_shouldNotCallGemini_whenDailyLimitIsReached() {
        when(rateLimiter.tryAcquire(startsWith("gemini:requests:"), eq(100), any())).thenReturn(false);

        assertThat(service.generateSentences(List.of(dry))).isEmpty();
        assertThat(calledModels).isEmpty();
    }

    @Test
    void generateSentences_shouldKeepOnlySentencesContainingTheExactWord() throws Exception {
        when(rateLimiter.tryAcquire(startsWith("gemini:requests:"), eq(100), any())).thenReturn(true);
        // 渇く bị chia thành 渇いた nên không chứa đúng nguyên văn từ -> bị loại.
        nextResponse = geminiReply("```json\n[{\"id\":1,\"sentence\":\"洗濯物がすぐ乾く。\"},"
                + "{\"id\":2,\"sentence\":\"のどが渇いた。\"}]\n```");

        assertThat(service.generateSentences(List.of(dry, thirsty))).contains(Map.of(1L, "洗濯物がすぐ乾く。"));
        assertThat(calledModels).containsExactly("test-model");
    }

    @Test
    void generateSentences_shouldReturnAnsweredButEmpty_whenNoSentenceIsUsable() throws Exception {
        when(rateLimiter.tryAcquire(startsWith("gemini:requests:"), eq(100), any())).thenReturn(true);
        nextResponse = geminiReply("[{\"id\":2,\"sentence\":\"のどが渇いた。\"}]");

        assertThat(service.generateSentences(List.of(thirsty))).contains(Map.of());
    }

    @Test
    void generateSentences_shouldReturnNoAnswer_whenReplyIsNotAJsonArray() throws Exception {
        when(rateLimiter.tryAcquire(startsWith("gemini:requests:"), eq(100), any())).thenReturn(true);
        nextResponse = geminiReply("Xin lỗi, tôi không thể trả lời yêu cầu này.");

        assertThat(service.generateSentences(List.of(dry))).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> geminiReply(String text) throws Exception {
        String body = new ObjectMapper().writeValueAsString(Map.of(
                "candidates", List.of(Map.of("content", Map.of("parts", List.of(Map.of("text", text)))))));
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body);
        return response;
    }
}
