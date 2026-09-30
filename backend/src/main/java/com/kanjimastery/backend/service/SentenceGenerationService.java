package com.kanjimastery.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanjimastery.backend.model.Kanji;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Sinh câu ví dụ tiếng Nhật (kiểu đề JLPT) cho từ vựng bằng Google Gemini API, theo lô:
 * một request trả về câu cho nhiều từ để tiết kiệm hạn mức (gói miễn phí tính theo số request/ngày).
 * <p>
 * Hỗ trợ nhiều API key và nhiều model (đều phân tách bằng dấu phẩy). Hạn mức miễn phí tính theo
 * project + model, nên khi model đầu tiên hết lượt/quá tải thì chuyển sang model tiếp theo.
 * Không cấu hình key nào thì trả rỗng - quiz vẫn hoạt động, chỉ thiếu câu ngữ cảnh.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SentenceGenerationService {

    private static final String GEMINI_ENDPOINT_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
    private static final Pattern RETRY_AFTER = Pattern.compile("retry in ([\\d.]+)s");
    private static final long DEFAULT_COOLDOWN_MS = 60_000;
    private static final long DAILY_QUOTA_COOLDOWN_MS = 60 * 60_000;
    private static final long OVERLOADED_COOLDOWN_MS = 30_000;
    private static final long UNAVAILABLE_MODEL_COOLDOWN_MS = 60 * 60_000;
    private static final String DAILY_COUNTER_PREFIX = "gemini:requests:";

    private final ObjectMapper objectMapper;
    private final RateLimiterService rateLimiter;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${app.ai.gemini-api-key:}")
    private String apiKeysRaw;

    @Value("${app.ai.model:gemini-3.8-flash}")
    private String modelsRaw;

    /** Trần request Gemini mỗi ngày (UTC) cho cả hệ thống; {@code <= 0} là không giới hạn. */
    @Value("${app.ai.daily-request-limit:100}")
    private int dailyRequestLimit;

    private List<String> apiKeys = List.of();
    private List<String> models = List.of();
    private final AtomicInteger nextKey = new AtomicInteger();
    /** Thời điểm (epoch ms) được thử lại; khoá là "model" hoặc "model|apiKey". Không bao giờ ghi log khoá này. */
    private final Map<String, Long> cooldownUntil = new ConcurrentHashMap<>();

    @PostConstruct
    void loadConfig() {
        apiKeys = splitList(apiKeysRaw);
        models = splitList(modelsRaw);
        log.info("Gemini: {} API key, model theo thứ tự ưu tiên {}", apiKeys.size(), models);
    }

    private static List<String> splitList(String raw) {
        return Arrays.stream(raw.split("[,;\\s]+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    public boolean isEnabled() {
        return !apiKeys.isEmpty() && !models.isEmpty();
    }

    /**
     * Sinh câu ví dụ cho nhiều từ trong MỘT request. Trả về map kanjiId -> câu (chỉ những câu hợp lệ),
     * hoặc rỗng khi không có câu trả lời dùng được từ Gemini (chưa cấu hình key, hết hạn mức hoặc hết trần
     * trong ngày, lỗi mạng, JSON hỏng). Tách hai trường hợp để phía gọi chỉ tính "từ sinh câu thất bại" khi
     * Gemini thật sự đã trả lời mà câu của từ đó vẫn không dùng được.
     */
    public Optional<Map<Long, String>> generateSentences(List<Kanji> kanjis) {
        if (!isEnabled() || kanjis.isEmpty()) {
            return Optional.empty();
        }
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "contents", List.of(Map.of("parts", List.of(Map.of("text", buildBatchPrompt(kanjis))))),
                    "generationConfig", Map.of("responseMimeType", "application/json")));
            return callWithFallback(requestBody).flatMap(text -> parseBatch(text, kanjis));
        } catch (Exception ex) {
            log.warn("Không sinh được câu ví dụ cho {} từ: {}", kanjis.size(), ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> callWithFallback(String requestBody) throws Exception {
        for (String model : models) {
            if (isCoolingDown(model)) {
                continue;
            }
            int start = Math.floorMod(nextKey.getAndIncrement(), apiKeys.size());
            for (int attempt = 0; attempt < apiKeys.size(); attempt++) {
                int keyIndex = (start + attempt) % apiKeys.size();
                String apiKey = apiKeys.get(keyIndex);
                String keyModel = model + "|" + apiKey;
                if (isCoolingDown(keyModel)) {
                    continue;
                }
                if (!withinDailyLimit()) {
                    log.warn("Đã dùng hết {} request Gemini của hôm nay (UTC) - tạm dừng sinh câu ví dụ tới ngày mai",
                            dailyRequestLimit);
                    return Optional.empty();
                }

                HttpResponse<String> response = send(model, apiKey, requestBody);
                int status = response.statusCode();
                if (status == 200) {
                    return Optional.of(extractText(response.body()));
                }
                if (status == 429) {
                    String violations = quotaViolations(response.body());
                    long cooldownMs = violations.contains("PerDay") ? DAILY_QUOTA_COOLDOWN_MS : retryAfterMillis(response.body());
                    cooldownUntil.put(keyModel, System.currentTimeMillis() + cooldownMs);
                    log.warn("Gemini {} (key #{}) hết hạn mức ({}), tạm nghỉ {}s",
                            model, keyIndex + 1, violations, cooldownMs / 1000);
                    continue;
                }
                long modelCooldown = status >= 500 ? OVERLOADED_COOLDOWN_MS : UNAVAILABLE_MODEL_COOLDOWN_MS;
                cooldownUntil.put(model, System.currentTimeMillis() + modelCooldown);
                log.warn("Gemini {} lỗi {} - chuyển sang model khác: {}", model, status, errorMessage(response.body()));
                break;
            }
        }
        log.warn("Không còn model/key Gemini nào khả dụng lúc này - bỏ qua lượt sinh câu ví dụ");
        return Optional.empty();
    }

    private boolean isCoolingDown(String cooldownKey) {
        return cooldownUntil.getOrDefault(cooldownKey, 0L) > System.currentTimeMillis();
    }

    /** Đếm request sắp gửi vào trần của ngày (UTC). Đếm cả request bị Gemini từ chối, để ước lượng luôn dư an toàn. */
    private boolean withinDailyLimit() {
        if (dailyRequestLimit <= 0) {
            return true;
        }
        String key = DAILY_COUNTER_PREFIX + LocalDate.now(ZoneOffset.UTC);
        return rateLimiter.tryAcquire(key, dailyRequestLimit, Duration.ofDays(2));
    }

    /** Package-private để test thay bằng phản hồi giả, không gọi mạng thật. */
    HttpResponse<String> send(String model, String apiKey, String requestBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(GEMINI_ENDPOINT_TEMPLATE.formatted(model)))
                .header("content-type", "application/json")
                .header("x-goog-api-key", apiKey)
                .timeout(Duration.ofSeconds(45))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String extractText(String responseBody) throws Exception {
        JsonNode parts = objectMapper.readTree(responseBody).path("candidates").path(0).path("content").path("parts");
        StringBuilder text = new StringBuilder();
        for (JsonNode part : parts) {
            text.append(part.path("text").asText(""));
        }
        return text.toString().trim();
    }

    /** Rỗng nếu phản hồi không phải mảng JSON đọc được - lỗi của cả lượt gọi, không tính cho từng từ. */
    private Optional<Map<Long, String>> parseBatch(String text, List<Kanji> kanjis) {
        Map<Long, Kanji> byId = kanjis.stream().collect(Collectors.toMap(Kanji::getId, Function.identity()));
        Map<Long, String> result = new HashMap<>();
        try {
            // Phòng khi model vẫn bọc JSON trong khối ```json ... ```.
            String json = text.replaceAll("(?s)^```(?:json)?\\s*|\\s*```$", "");
            JsonNode items = objectMapper.readTree(json);
            if (!items.isArray()) {
                log.warn("Gemini trả về JSON không phải dạng mảng - bỏ qua lượt này");
                return Optional.empty();
            }
            for (JsonNode item : items) {
                Kanji kanji = byId.get(item.path("id").asLong());
                String sentence = item.path("sentence").asText("").trim().replaceAll("^[\"「]|[\"」]$", "").trim();
                if (kanji != null && StringUtils.hasText(sentence) && sentence.contains(kanji.getCharacter())) {
                    result.put(kanji.getId(), sentence);
                }
            }
        } catch (Exception ex) {
            log.warn("Không đọc được JSON câu ví dụ từ Gemini: {}", ex.getMessage());
            return Optional.empty();
        }
        log.info("Gemini sinh được {}/{} câu ví dụ hợp lệ", result.size(), kanjis.size());
        return Optional.of(result);
    }

    /** Tên các hạn mức bị vượt (vd. "GenerateRequestsPerDayPerProjectPerModel-FreeTier=20"). */
    private String quotaViolations(String responseBody) {
        try {
            List<String> violations = new ArrayList<>();
            for (JsonNode detail : objectMapper.readTree(responseBody).path("error").path("details")) {
                for (JsonNode violation : detail.path("violations")) {
                    violations.add(violation.path("quotaId").asText() + "=" + violation.path("quotaValue").asText());
                }
            }
            return violations.isEmpty() ? "không rõ hạn mức" : String.join(", ", violations);
        } catch (Exception ex) {
            return "không đọc được chi tiết hạn mức";
        }
    }

    private String errorMessage(String responseBody) {
        try {
            String message = objectMapper.readTree(responseBody).path("error").path("message").asText("");
            return message.length() > 160 ? message.substring(0, 160) + "..." : message;
        } catch (Exception ex) {
            return "";
        }
    }

    private long retryAfterMillis(String responseBody) {
        Matcher matcher = RETRY_AFTER.matcher(responseBody);
        if (matcher.find()) {
            return (long) (Double.parseDouble(matcher.group(1)) * 1000) + 1000;
        }
        return DEFAULT_COOLDOWN_MS;
    }

    private String buildBatchPrompt(List<Kanji> kanjis) {
        String wordList = kanjis.stream()
                .map(k -> "- id=%d | từ: %s | nghĩa: %s | cấp độ: %s".formatted(k.getId(), k.getCharacter(), k.getMeaning(), k.getJlptLevel()))
                .collect(Collectors.joining("\n"));
        return """
                Bạn là giáo viên tiếng Nhật đang soạn đề thi JLPT. Với MỖI từ trong danh sách dưới đây, \
                viết đúng MỘT câu tiếng Nhật tự nhiên, độ khó tương đương cấp độ JLPT ghi kèm, \
                dùng từ đúng với nghĩa đã cho.

                QUY TẮC BẮT BUỘC: câu phải chứa từ đó đúng NGUYÊN VĂN từng ký tự như trong danh sách. \
                Với động từ và tính từ, KHÔNG được chia (không dùng dạng ます, て, た, ない, かった...): \
                hãy giữ dạng từ điển bằng các mẫu như 〜ことが/〜ことにする, 〜のは, 〜前に, 〜ように, 〜はずだ, \
                〜と思う, hoặc đặt ngay trước danh từ.
                Ví dụ với từ 残す: đúng 「料理を残すのはもったいない。」, sai 「料理を残しました。」.
                Ví dụ với từ 面白い: đúng 「これは面白い映画だ。」, sai 「映画は面白かった。」.

                Chỉ trả về JSON là một mảng dạng [{"id": <id>, "sentence": "<câu tiếng Nhật>"}], không giải thích, \
                không dịch nghĩa.

                Danh sách từ:
                %s
                """.formatted(wordList);
    }
}
