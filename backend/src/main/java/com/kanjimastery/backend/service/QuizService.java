package com.kanjimastery.backend.service;

import com.kanjimastery.backend.config.RateLimitProperties;
import com.kanjimastery.backend.dto.QuizQuestionResponse;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.exception.TooManyRequestsException;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.repository.KanjiRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Sinh bộ câu hỏi trắc nghiệm ôn tập (không lưu kết quả, không ảnh hưởng lịch SRS) từ
 * danh sách từ vựng đã lọc theo tag/cấp độ. Câu hỏi có câu ví dụ theo kiểu đề JLPT
 * (問題1 漢字読み / 問題2 表記) khi đã sinh được câu ví dụ cho từ đó.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuizService {

    /** Thời gian tối đa chờ AI sinh câu ví dụ cho một lượt tạo quiz - quá hạn thì dùng câu hỏi không có ngữ cảnh. */
    private static final long SENTENCE_BUDGET_MS = 15_000;
    /** Số từ tối đa trong một request sinh câu (một request cho cả bài để tiết kiệm hạn mức). */
    private static final int SENTENCE_BATCH_SIZE = 40;
    /** Gemini trả lời mà câu của một từ vẫn bị loại chừng này lần thì tạm bỏ qua từ đó, khỏi tốn request mỗi lần mở quiz. */
    private static final int MAX_SENTENCE_FAILURES = 2;
    private static final Duration SENTENCE_FAILURE_WINDOW = Duration.ofHours(24);
    private static final String SENTENCE_FAILURE_PREFIX = "sentence:failures:";

    private final KanjiRepository kanjiRepository;
    private final SentenceGenerationService sentenceGenerationService;
    private final RateLimiterService rateLimiter;
    private final RateLimitProperties rateLimitProperties;
    private final ExecutorService sentenceExecutor = Executors.newFixedThreadPool(4);
    /** Từ đang được sinh câu ở một request khác - tránh gửi trùng khi người học mở quiz liên tục. */
    private final Set<Long> sentencesInFlight = ConcurrentHashMap.newKeySet();

    @PreDestroy
    void shutdownExecutor() {
        sentenceExecutor.shutdownNow();
    }

    @Transactional(readOnly = true)
    public List<QuizQuestionResponse> generate(String username, Long tagId, String level, Integer size) {
        RateLimitProperties.Bucket limit = rateLimitProperties.getQuiz();
        if (!rateLimiter.tryAcquire("ratelimit:quiz:" + username, limit.getLimit(), Duration.ofSeconds(limit.getWindowSeconds()))) {
            throw new TooManyRequestsException("Bạn tạo trắc nghiệm quá nhanh. Vui lòng đợi một chút rồi thử lại.");
        }

        String normalizedLevel = StringUtils.hasText(level) ? level.toUpperCase() : null;
        List<Kanji> pool = kanjiRepository.findAllByFilters(normalizedLevel, tagId);
        if (pool.isEmpty()) {
            throw new BadRequestException("Không có từ vựng nào phù hợp bộ lọc đã chọn");
        }

        List<Kanji> shuffledPool = new ArrayList<>(pool);
        Collections.shuffle(shuffledPool);
        int questionCount = Math.min(size == null || size < 1 ? 10 : size, shuffledPool.size());
        List<Kanji> selected = shuffledPool.subList(0, questionCount);

        Map<Long, String> newSentences = generateMissingSentences(selected, pool);

        List<QuizQuestionResponse> questions = new ArrayList<>();
        for (Kanji kanji : selected) {
            String sentence = StringUtils.hasText(kanji.getExampleSentence())
                    ? kanji.getExampleSentence()
                    : newSentences.get(kanji.getId());
            questions.add(buildQuestion(kanji, sentence, pool));
        }
        return questions;
    }

    /**
     * Gửi MỘT request sinh câu cho các từ chưa có câu ví dụ: ưu tiên từ trong quiz lần này, rồi lấp thêm
     * các từ khác cùng bài để lần sau khỏi gọi lại. Bỏ qua từ vừa sinh câu thất bại nhiều lần. Chờ tối đa
     * {@link #SENTENCE_BUDGET_MS}; nếu xong muộn hơn thì câu vẫn được lưu vào DB và dùng cho lần tạo quiz sau.
     */
    private Map<Long, String> generateMissingSentences(List<Kanji> selected, List<Kanji> pool) {
        if (!sentenceGenerationService.isEnabled()) {
            return Map.of();
        }

        Set<Long> seen = new HashSet<>();
        List<Kanji> missing = new ArrayList<>();
        for (List<Kanji> source : List.of(selected, pool)) {
            for (Kanji kanji : source) {
                if (!StringUtils.hasText(kanji.getExampleSentence()) && seen.add(kanji.getId())) {
                    missing.add(kanji);
                }
            }
        }
        if (missing.isEmpty()) {
            return Map.of();
        }

        Set<Long> recentlyFailed = recentlyFailed(missing);
        List<Kanji> batch = new ArrayList<>();
        for (Kanji kanji : missing) {
            if (batch.size() >= SENTENCE_BATCH_SIZE) break;
            if (!recentlyFailed.contains(kanji.getId()) && sentencesInFlight.add(kanji.getId())) {
                batch.add(kanji);
            }
        }
        if (batch.isEmpty()) {
            return Map.of();
        }

        List<Long> batchIds = batch.stream().map(Kanji::getId).toList();
        CompletableFuture<Optional<Map<Long, String>>> future = CompletableFuture
                .supplyAsync(() -> sentenceGenerationService.generateSentences(batch), sentenceExecutor)
                .whenComplete((result, error) -> {
                    try {
                        if (result != null && result.isPresent()) {
                            Map<Long, String> sentences = result.get();
                            sentences.forEach(kanjiRepository::saveExampleSentenceIfAbsent);
                            // Gemini đã trả lời mà câu của các từ này vẫn không dùng được: ghi nhận để không gửi lại mãi.
                            batch.stream()
                                    .filter(kanji -> !sentences.containsKey(kanji.getId()))
                                    .forEach(kanji -> rateLimiter.increment(
                                            SENTENCE_FAILURE_PREFIX + kanji.getId(), SENTENCE_FAILURE_WINDOW));
                        }
                    } finally {
                        batchIds.forEach(sentencesInFlight::remove);
                    }
                });

        try {
            return future.get(SENTENCE_BUDGET_MS, TimeUnit.MILLISECONDS).orElse(Map.of());
        } catch (TimeoutException ex) {
            log.debug("Hết thời gian chờ AI sinh câu ví dụ - quiz lần này dùng câu hỏi không có ngữ cảnh");
        } catch (Exception ex) {
            log.warn("Lỗi khi chờ AI sinh câu ví dụ: {}", ex.getMessage());
        }
        return Map.of();
    }

    /** Từ đã sinh câu thất bại {@link #MAX_SENTENCE_FAILURES} lần trong vòng 24 giờ - đọc một lần từ Redis cho cả danh sách. */
    private Set<Long> recentlyFailed(List<Kanji> kanjis) {
        List<String> keys = kanjis.stream().map(kanji -> SENTENCE_FAILURE_PREFIX + kanji.getId()).toList();
        List<Long> failures = rateLimiter.counts(keys);
        Set<Long> skipped = new HashSet<>();
        for (int i = 0; i < kanjis.size(); i++) {
            if (failures.get(i) >= MAX_SENTENCE_FAILURES) {
                skipped.add(kanjis.get(i).getId());
            }
        }
        return skipped;
    }

    private QuizQuestionResponse buildQuestion(Kanji kanji, String exampleSentence, List<Kanji> pool) {
        String reading = kanji.getReading();

        String direction;
        Function<Kanji, String> optionExtractor;
        String correctAnswer;

        if (StringUtils.hasText(reading)) {
            direction = ThreadLocalRandom.current().nextBoolean() ? "KANJI_TO_READING" : "READING_TO_KANJI";
            if ("KANJI_TO_READING".equals(direction)) {
                optionExtractor = Kanji::getReading;
                correctAnswer = reading;
            } else {
                optionExtractor = Kanji::getCharacter;
                correctAnswer = kanji.getCharacter();
            }
        } else {
            direction = "MEANING";
            optionExtractor = Kanji::getMeaning;
            correctAnswer = kanji.getMeaning();
        }

        List<String> choices = buildChoices(kanji, pool, optionExtractor, correctAnswer);
        String prompt = "READING_TO_KANJI".equals(direction) ? reading : kanji.getCharacter();

        return QuizQuestionResponse.builder()
                .kanjiId(kanji.getId())
                .direction(direction)
                .prompt(prompt)
                .sentence(sentenceForQuestion(exampleSentence, kanji, direction))
                .choices(choices)
                .correctIndex(choices.indexOf(correctAnswer))
                .character(kanji.getCharacter())
                .reading(reading)
                .meaning(kanji.getMeaning())
                .build();
    }

    /**
     * Kiểu đề JLPT: hỏi cách đọc thì giữ từ dạng Kanji trong câu; hỏi cách viết thì thay từ đó
     * bằng hiragana. Frontend gạch chân {@code prompt} bên trong câu.
     */
    private String sentenceForQuestion(String exampleSentence, Kanji kanji, String direction) {
        if (!StringUtils.hasText(exampleSentence) || !exampleSentence.contains(kanji.getCharacter())) {
            return null;
        }
        if (!"READING_TO_KANJI".equals(direction)) {
            return exampleSentence;
        }
        // Cách đọc dạng "み(る)" (chữ Hán đơn kèm đuôi) không thay thẳng vào câu được.
        if (kanji.getReading().contains("(")) {
            return null;
        }
        return exampleSentence.replaceFirst(Pattern.quote(kanji.getCharacter()), Matcher.quoteReplacement(kanji.getReading()));
    }

    private List<String> buildChoices(Kanji kanji, List<Kanji> pool, Function<Kanji, String> optionExtractor, String correctAnswer) {
        Set<String> distractors = new LinkedHashSet<>();
        List<Kanji> others = pool.stream().filter(k -> !k.getId().equals(kanji.getId())).collect(Collectors.toList());
        Collections.shuffle(others);
        for (Kanji other : others) {
            if (distractors.size() >= 3) break;
            String value = optionExtractor.apply(other);
            if (StringUtils.hasText(value) && !value.equals(correctAnswer)) {
                distractors.add(value);
            }
        }

        List<String> choices = new ArrayList<>(distractors);
        choices.add(correctAnswer);
        Collections.shuffle(choices);
        return choices;
    }
}
