package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.repository.KanjiRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Nhờ AI (Gemini, qua {@link SentenceGenerationService}) viết câu ví dụ cho các từ trong bài học còn thiếu câu, không
 * cần đợi có người mở quiz. Mỗi request viết cho {@link #BATCH_SIZE} từ; số request mỗi lượt có trần riêng để chừa hạn
 * mức Gemini trong ngày cho người học. Câu lưu kèm nguồn AI, nên câu thi dựng từ câu đó phải chờ duyệt.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExampleSentenceService {

    /** Số từ trong một request, như khi sinh câu lúc làm quiz. */
    static final int BATCH_SIZE = 40;
    /** Gemini đã trả lời mà câu của một từ vẫn không dùng được chừng này lần thì tạm bỏ qua từ đó. */
    static final int MAX_FAILURES = 2;
    private static final Duration FAILURE_WINDOW = Duration.ofDays(7);
    private static final String FAILURE_PREFIX = "sentence:job-failures:";

    private final KanjiRepository kanjiRepository;
    private final SentenceGenerationService sentenceGenerationService;
    private final RateLimiterService rateLimiter;

    /**
     * @param requests      số request Gemini đã gửi
     * @param saved         số câu đã lưu
     * @param stillMissing  số từ trong bài học còn chưa có câu sau lượt này
     * @param stopped       lý do dừng sớm (chưa cấu hình AI, Gemini không trả lời / hết hạn mức), null nếu chạy đủ
     */
    public record Result(int requests, int saved, long stillMissing, String stopped) {
    }

    /** Viết câu cho các từ còn thiếu, tối đa {@code maxRequests} request. Không mở transaction: mỗi câu lưu riêng. */
    public Result fillMissing(int maxRequests) {
        if (!sentenceGenerationService.isEnabled()) {
            return new Result(0, 0, kanjiRepository.countWithoutExampleSentence(), "chưa cấu hình AI (GEMINI_API_KEY)");
        }
        // Lấy dư để còn đủ từ sau khi bỏ các từ vừa thất bại.
        List<Kanji> candidates = kanjiRepository.findWithoutExampleSentence(Math.max(maxRequests, 0) * BATCH_SIZE * 2);
        List<Long> failures = rateLimiter.counts(candidates.stream().map(word -> FAILURE_PREFIX + word.getId()).toList());
        List<Kanji> words = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            if (failures.get(i) < MAX_FAILURES) {
                words.add(candidates.get(i));
            }
        }
        int requests = 0;
        int saved = 0;
        String stopped = null;
        for (int from = 0; from < words.size() && requests < maxRequests; from += BATCH_SIZE) {
            List<Kanji> batch = words.subList(from, Math.min(from + BATCH_SIZE, words.size()));
            Optional<Map<Long, String>> answer = sentenceGenerationService.generateSentences(batch);
            if (answer.isEmpty()) {
                stopped = "Gemini không trả lời được (hết hạn mức trong ngày hoặc lỗi mạng)";
                break;
            }
            requests++;
            Map<Long, String> sentences = answer.get();
            for (Kanji word : batch) {
                String sentence = sentences.get(word.getId());
                if (sentence == null) {
                    rateLimiter.increment(FAILURE_PREFIX + word.getId(), FAILURE_WINDOW);
                } else {
                    saved += kanjiRepository.saveExampleSentenceIfAbsent(word.getId(), sentence);
                }
            }
        }
        long stillMissing = kanjiRepository.countWithoutExampleSentence();
        log.info("AI viết câu ví dụ: {} request, lưu {} câu, còn {} từ chưa có câu{}", requests, saved, stillMissing,
                stopped == null ? "" : " - dừng: " + stopped);
        return new Result(requests, saved, stillMissing, stopped);
    }
}
