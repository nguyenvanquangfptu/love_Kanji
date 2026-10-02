package com.kanjimastery.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanjimastery.backend.model.Kanji;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sinh câu ví dụ tiếng Nhật (kiểu đề JLPT) cho từ vựng bằng Gemini ({@link GeminiClient}), theo lô:
 * một request trả về câu cho nhiều từ để tiết kiệm hạn mức (gói miễn phí tính theo số request/ngày).
 * Chưa cấu hình AI thì trả rỗng - quiz vẫn hoạt động, chỉ thiếu câu ngữ cảnh.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SentenceGenerationService {

    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    public boolean isEnabled() {
        return geminiClient.isEnabled();
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
        return geminiClient.generateJson(buildBatchPrompt(kanjis)).flatMap(text -> parseBatch(text, kanjis));
    }

    /** Rỗng nếu phản hồi không phải mảng JSON đọc được - lỗi của cả lượt gọi, không tính cho từng từ. */
    private Optional<Map<Long, String>> parseBatch(String text, List<Kanji> kanjis) {
        Map<Long, Kanji> byId = kanjis.stream().collect(Collectors.toMap(Kanji::getId, Function.identity()));
        Map<Long, String> result = new HashMap<>();
        try {
            JsonNode items = objectMapper.readTree(GeminiClient.stripCodeFence(text));
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
