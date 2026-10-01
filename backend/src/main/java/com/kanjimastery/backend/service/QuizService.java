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
import java.util.Comparator;
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
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Sinh bộ câu hỏi trắc nghiệm ôn tập (không lưu kết quả, không ảnh hưởng lịch SRS) từ
 * danh sách từ vựng đã lọc theo tag (một bài) hoặc cấp độ (cả cấp độ). Câu hỏi có câu ví dụ theo kiểu đề JLPT
 * (問題1 漢字読み / 問題2 表記) khi đã sinh được câu ví dụ cho từ đó. Đáp án nhiễu là chữ Hán trông gần giống
 * hoặc cách đọc bẫy trường âm/âm ngắt/âm đục ({@link QuizDistractorGenerator}), thiếu mới bù bằng từ khác trong bài.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuizService {

    private static final String KANJI_TO_READING = "KANJI_TO_READING";
    private static final String READING_TO_KANJI = "READING_TO_KANJI";
    private static final String MEANING = "MEANING";
    private static final int DEFAULT_QUESTIONS = 10;
    /** Trắc nghiệm cả cấp độ có hàng nghìn từ - chặn số câu mỗi lượt để một request không dựng quá nhiều câu hỏi. */
    private static final int MAX_QUESTIONS = 50;
    private static final int DISTRACTOR_COUNT = 3;
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
    private final QuizDistractorGenerator distractorGenerator;
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
        // Cả cấp độ = mọi bài của cấp đó (N5-01..N5-25), không theo cột jlpt_level: từ như 意味 ghi N3 nhưng cũng học ở bài N5.
        List<Kanji> pool = tagId == null && normalizedLevel != null
                ? kanjiRepository.findAllByTagNamePrefix(normalizedLevel + "-%")
                : kanjiRepository.findAllByFilters(normalizedLevel, tagId);
        if (pool.isEmpty()) {
            throw new BadRequestException("Không có từ vựng nào phù hợp bộ lọc đã chọn");
        }

        List<Kanji> shuffledPool = new ArrayList<>(pool);
        Collections.shuffle(shuffledPool);
        int requested = size == null || size < 1 ? DEFAULT_QUESTIONS : Math.min(size, MAX_QUESTIONS);
        List<Kanji> selected = shuffledPool.subList(0, Math.min(requested, shuffledPool.size()));

        Map<Long, String> newSentences = generateMissingSentences(selected, pool);

        List<PlannedQuestion> plans = selected.stream().map(this::plan).toList();
        Map<String, String> existingWordReadings = readingsOfExistingWords(plans);

        List<QuizQuestionResponse> questions = new ArrayList<>();
        for (PlannedQuestion plan : plans) {
            Kanji kanji = plan.kanji();
            String sentence = StringUtils.hasText(kanji.getExampleSentence())
                    ? kanji.getExampleSentence()
                    : newSentences.get(kanji.getId());
            questions.add(buildQuestion(plan, sentence, pool, existingWordReadings));
        }
        return questions;
    }

    /** Hướng hỏi của một câu và các đáp án nhiễu gần đúng cho hướng đó (chưa lọc từ đồng âm). */
    private record PlannedQuestion(Kanji kanji, String direction, List<String> nearMisses) {
    }

    private PlannedQuestion plan(Kanji kanji) {
        if (!StringUtils.hasText(kanji.getReading())) {
            return new PlannedQuestion(kanji, MEANING, List.of());
        }
        return ThreadLocalRandom.current().nextBoolean()
                ? new PlannedQuestion(kanji, KANJI_TO_READING,
                        distractorGenerator.trapReadings(kanji.getReading(), kanji.getCharacter()))
                : new PlannedQuestion(kanji, READING_TO_KANJI,
                        distractorGenerator.lookAlikeSpellings(kanji.getCharacter()));
    }

    /**
     * Cách đọc của những cách viết nhiễu trùng với một từ có thật trong kho (vd. thay 会 cho 合 ra 会う) -
     * một truy vấn cho cả bài. Dùng để bỏ đáp án nhiễu đọc giống hệt đáp án đúng, vì khi đó cả hai đều "đúng".
     */
    private Map<String, String> readingsOfExistingWords(List<PlannedQuestion> plans) {
        Set<String> spellings = plans.stream()
                .filter(plan -> READING_TO_KANJI.equals(plan.direction()))
                .flatMap(plan -> plan.nearMisses().stream())
                .collect(Collectors.toSet());
        if (spellings.isEmpty()) {
            return Map.of();
        }
        return kanjiRepository.findAllByCharacterIn(spellings).stream()
                .filter(word -> StringUtils.hasText(word.getReading()))
                .collect(Collectors.toMap(Kanji::getCharacter, Kanji::getReading, (first, second) -> first));
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

    private QuizQuestionResponse buildQuestion(PlannedQuestion plan, String exampleSentence, List<Kanji> pool,
                                               Map<String, String> existingWordReadings) {
        Kanji kanji = plan.kanji();
        String direction = plan.direction();
        String reading = kanji.getReading();

        String correctAnswer;
        List<String> nearMisses;
        List<String> fallback;
        switch (direction) {
            case READING_TO_KANJI -> {
                correctAnswer = kanji.getCharacter();
                nearMisses = plan.nearMisses().stream()
                        .filter(spelling -> !reading.equals(existingWordReadings.get(spelling)))
                        .toList();
                fallback = spellingFallback(kanji, pool);
            }
            case KANJI_TO_READING -> {
                correctAnswer = reading;
                nearMisses = plan.nearMisses();
                fallback = readingFallback(kanji, pool);
            }
            default -> {
                correctAnswer = kanji.getMeaning();
                nearMisses = List.of();
                fallback = otherWords(kanji, pool).stream().map(Kanji::getMeaning).toList();
            }
        }

        List<String> choices = buildChoices(correctAnswer, nearMisses, fallback);
        String prompt = READING_TO_KANJI.equals(direction) ? reading : kanji.getCharacter();

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
        if (!READING_TO_KANJI.equals(direction)) {
            return exampleSentence;
        }
        // Cách đọc dạng "み(る)" (chữ Hán đơn kèm đuôi) không thay thẳng vào câu được.
        if (kanji.getReading().contains("(")) {
            return null;
        }
        return exampleSentence.replaceFirst(Pattern.quote(kanji.getCharacter()), Matcher.quoteReplacement(kanji.getReading()));
    }

    /** Ưu tiên đáp án nhiễu gần đúng, thiếu mới bù bằng {@code fallback}. */
    private static List<String> buildChoices(String correctAnswer, List<String> nearMisses, List<String> fallback) {
        Set<String> distractors = new LinkedHashSet<>();
        for (List<String> source : List.of(nearMisses, fallback)) {
            for (String value : source) {
                if (distractors.size() >= DISTRACTOR_COUNT) break;
                if (StringUtils.hasText(value) && !value.equals(correctAnswer)) {
                    distractors.add(value);
                }
            }
        }

        List<String> choices = new ArrayList<>(distractors);
        choices.add(correctAnswer);
        Collections.shuffle(choices);
        return choices;
    }

    /**
     * Bù cho câu hỏi chọn cách viết: từ khác trong bài có chữ Hán, không đọc giống đáp án (以外 cho いがい của 意外
     * cũng "đúng"). Ưu tiên từ cùng độ dài có chung một chữ Hán hoặc cùng đuôi okurigana (厚い với 高い, không phải 交番).
     */
    private static List<String> spellingFallback(Kanji kanji, List<Kanji> pool) {
        String word = kanji.getCharacter();
        Set<Integer> wordKanji = word.codePoints().filter(QuizDistractorGenerator::isKanji).boxed().collect(Collectors.toSet());
        int lastChar = word.codePointBefore(word.length());
        boolean kanaEnding = !QuizDistractorGenerator.isKanji(lastChar);
        return rankBySimilarity(otherWords(kanji, pool).stream()
                        .filter(other -> other.getCharacter().codePoints().anyMatch(QuizDistractorGenerator::isKanji))
                        .filter(other -> !kanji.getReading().equals(other.getReading()))
                        .map(Kanji::getCharacter)
                        .toList(),
                word,
                other -> other.codePoints().anyMatch(wordKanji::contains)
                        || kanaEnding && other.codePointBefore(other.length()) == lastChar);
    }

    /** Bù cho câu hỏi chọn cách đọc: ưu tiên cách đọc cùng độ dài và cùng đuôi (動詞 〜める với 〜める). */
    private static List<String> readingFallback(Kanji kanji, List<Kanji> pool) {
        String reading = kanji.getReading();
        char ending = reading.charAt(reading.length() - 1);
        return rankBySimilarity(otherWords(kanji, pool).stream()
                        .map(Kanji::getReading)
                        .filter(StringUtils::hasText)
                        .toList(),
                reading,
                other -> other.charAt(other.length() - 1) == ending);
    }

    /** Xếp hạng giữ ngẫu nhiên trong từng nhóm: cùng độ dài + giống nhau, cùng độ dài, giống nhau, còn lại. */
    private static List<String> rankBySimilarity(List<String> candidates, String target, Predicate<String> alike) {
        List<String> ranked = new ArrayList<>(candidates);
        Collections.shuffle(ranked);
        ranked.sort(Comparator.comparingInt(candidate ->
                (candidate.length() == target.length() ? 0 : 2) + (alike.test(candidate) ? 0 : 1)));
        return ranked;
    }

    private static List<Kanji> otherWords(Kanji kanji, List<Kanji> pool) {
        List<Kanji> others = pool.stream().filter(k -> !k.getId().equals(kanji.getId())).collect(Collectors.toList());
        Collections.shuffle(others);
        return others;
    }
}
