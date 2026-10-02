package com.kanjimastery.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanjimastery.backend.config.RateLimitProperties;
import com.kanjimastery.backend.dto.MnemonicResponse;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.exception.ResourceNotFoundException;
import com.kanjimastery.backend.exception.TooManyRequestsException;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.repository.KanjiRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Optional;

/**
 * Mẹo nhớ cho từ vựng, do Gemini sinh dựa trên âm Hán Việt - lợi thế sẵn có của người Việt khi học chữ Hán.
 * Mỗi từ chỉ sinh một lần rồi lưu vào {@code kanji.mnemonic} cho mọi người dùng chung, để tiết kiệm hạn mức AI.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MnemonicService {

    /** Dài hơn chừng này ký tự thì coi là Gemini trả lời lạc đề. */
    static final int MAX_MNEMONIC_LENGTH = 400;

    private final GeminiClient geminiClient;
    private final KanjiRepository kanjiRepository;
    private final ObjectMapper objectMapper;
    private final RateLimiterService rateLimiter;
    private final RateLimitProperties rateLimitProperties;

    /**
     * Có sẵn thì trả luôn, chưa có thì nhờ Gemini sinh rồi lưu lại. Xoá cache của từ để lần đọc sau thấy mẹo nhớ.
     * Chỉ lượt thật sự gọi AI mới bị tính vào giới hạn theo tài khoản.
     */
    @CacheEvict(value = "kanji", key = "#kanjiId")
    public MnemonicResponse getOrGenerate(String username, Long kanjiId) {
        Kanji kanji = kanjiRepository.findById(kanjiId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Kanji với id: " + kanjiId));
        if (StringUtils.hasText(kanji.getMnemonic())) {
            return new MnemonicResponse(kanji.getMnemonic());
        }
        if (!geminiClient.isEnabled()) {
            throw new BadRequestException("Chưa bật tính năng AI (GEMINI_API_KEY) nên chưa gợi ý được mẹo nhớ");
        }
        RateLimitProperties.Bucket limit = rateLimitProperties.getMnemonic();
        if (!rateLimiter.tryAcquire("ratelimit:mnemonic:" + username, limit.getLimit(),
                Duration.ofSeconds(limit.getWindowSeconds()))) {
            throw new TooManyRequestsException("Bạn đã nhờ AI gợi ý nhiều lần liên tiếp. Vui lòng thử lại sau ít phút.");
        }

        String mnemonic = geminiClient.generateJson(buildPrompt(kanji))
                .flatMap(this::parse)
                .orElseThrow(() -> new TooManyRequestsException("AI đang bận hoặc đã hết lượt hôm nay - thử lại sau nhé."));
        if (kanjiRepository.saveMnemonicIfAbsent(kanjiId, mnemonic) == 0) {
            // Người khác vừa lưu trước: dùng bản đã lưu để mọi người thấy chung một mẹo nhớ.
            mnemonic = kanjiRepository.findMnemonicById(kanjiId).orElse(mnemonic);
        }
        return new MnemonicResponse(mnemonic);
    }

    private Optional<String> parse(String text) {
        try {
            String mnemonic = objectMapper.readTree(GeminiClient.stripCodeFence(text)).path("mnemonic").asText("").trim();
            if (!StringUtils.hasText(mnemonic) || mnemonic.length() > MAX_MNEMONIC_LENGTH) {
                log.warn("Gemini trả về mẹo nhớ rỗng hoặc quá dài ({} ký tự) - bỏ qua", mnemonic.length());
                return Optional.empty();
            }
            return Optional.of(mnemonic);
        } catch (Exception ex) {
            log.warn("Không đọc được JSON mẹo nhớ từ Gemini: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private String buildPrompt(Kanji kanji) {
        String reading = StringUtils.hasText(kanji.getReading()) ? kanji.getReading() : kanji.getCharacter();
        String hanViet = StringUtils.hasText(kanji.getHanViet()) ? kanji.getHanViet() : "(không có - từ không có chữ Hán)";
        return """
                Bạn là giáo viên tiếng Nhật dạy người Việt. Viết MỘT mẹo nhớ ngắn (tối đa 2 câu, bằng tiếng Việt) \
                giúp nhớ từ dưới đây. Ưu tiên nối âm Hán Việt của từng chữ Hán với nghĩa của từ, vì người Việt đã quen \
                âm Hán Việt; có thể dùng thêm hình dạng hoặc bộ thủ của chữ. Nếu cách đọc dễ nhầm (trường âm, âm ngắt っ, \
                âm đục), thêm một vế nhắc cách phân biệt. Không tự đổi âm Hán Việt đã cho.

                Từ: %s | cách đọc: %s | âm Hán Việt: %s | nghĩa: %s

                Chỉ trả về JSON dạng {"mnemonic": "<mẹo nhớ>"}, không giải thích thêm.
                """.formatted(kanji.getCharacter(), reading, hanViet, kanji.getMeaning());
    }
}
