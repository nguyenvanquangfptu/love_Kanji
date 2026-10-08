package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.QuizDirection;
import com.kanjimastery.backend.config.RateLimitProperties;
import com.kanjimastery.backend.dto.QuizQuestionResponse;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.exception.TooManyRequestsException;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.repository.KanjiRepository;
import com.kanjimastery.backend.service.LearnerHistoryService.PastMistake;
import com.kanjimastery.backend.service.QuestionBuilder.BuiltQuestion;
import com.kanjimastery.backend.service.QuestionBuilder.PlannedQuestion;
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
import java.util.LinkedHashMap;
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
import java.util.random.RandomGenerator;

import static com.kanjimastery.backend.model.QuizDirection.KANJI_TO_READING;
import static com.kanjimastery.backend.model.QuizDirection.MEANING;
import static com.kanjimastery.backend.model.QuizDirection.READING_TO_KANJI;
import static com.kanjimastery.backend.model.QuizDirection.TYPE_READING;

/**
 * Sinh bộ câu hỏi trắc nghiệm ôn tập (kết quả từng câu được ghi qua {@link QuizAnswerService}) từ
 * danh sách từ vựng đã lọc theo tag (một bài) hoặc cấp độ (cả cấp độ), từ khó của người học, hoặc một danh sách từ
 * cho trước (các từ làm sai trong một bài thi). Mặc định chọn từ và hướng hỏi theo điểm yếu
 * của người học ({@link AdaptiveQuizPlanner}); chế độ {@link #MODE_RANDOM} chọn ngẫu nhiên đều để kiểm tra cả bài.
 * Câu hỏi có câu ví dụ theo kiểu đề JLPT khi đã sinh được câu ví dụ cho từ đó (AI sinh thêm khi thiếu); cách dựng
 * câu hỏi và đáp án nhiễu dùng chung với câu thi ({@link QuestionBuilder}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuizService {

    public static final String MODE_ADAPTIVE = "adaptive";
    public static final String MODE_RANDOM = "random";
    /** Kiểu trả lời: chọn trong 4 đáp án, hoặc tự gõ cách đọc. */
    public static final String ANSWER_CHOICE = "choice";
    public static final String ANSWER_TYPING = "typing";
    private static final int DEFAULT_QUESTIONS = 10;
    /** Trắc nghiệm cả cấp độ có hàng nghìn từ - chặn số câu mỗi lượt để một request không dựng quá nhiều câu hỏi. */
    private static final int MAX_QUESTIONS = 50;
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
    private final QuestionBuilder questionBuilder;
    private final UserService userService;
    private final LearnerHistoryService learnerHistoryService;
    private final SrsService srsService;
    private final ExecutorService sentenceExecutor = Executors.newFixedThreadPool(4);
    /** Từ đang được sinh câu ở một request khác - tránh gửi trùng khi người học mở quiz liên tục. */
    private final Set<Long> sentencesInFlight = ConcurrentHashMap.newKeySet();

    @PreDestroy
    void shutdownExecutor() {
        sentenceExecutor.shutdownNow();
    }

    /**
     * @param kanjiIds nếu có: hỏi đúng các từ này (bỏ qua tagId/level/hardWords), đáp án nhiễu lấy từ các từ cùng bài
     */
    @Transactional(readOnly = true)
    public List<QuizQuestionResponse> generate(String username, Long tagId, String level, Integer size, String mode,
                                               boolean hardWords, List<Long> kanjiIds) {
        return generate(username, tagId, level, size, mode, hardWords, kanjiIds, ANSWER_CHOICE);
    }

    /**
     * @param answer {@link #ANSWER_TYPING}: mọi câu hỏi gõ cách đọc, chỉ lấy từ có chữ Hán và cách đọc
     */
    @Transactional(readOnly = true)
    public List<QuizQuestionResponse> generate(String username, Long tagId, String level, Integer size, String mode,
                                               boolean hardWords, List<Long> kanjiIds, String answer) {
        boolean typing = switch (answer == null ? ANSWER_CHOICE : answer) {
            case ANSWER_CHOICE -> false;
            case ANSWER_TYPING -> true;
            default -> throw new BadRequestException("answer phải là " + ANSWER_CHOICE + " hoặc " + ANSWER_TYPING);
        };
        boolean adaptive = switch (mode == null ? MODE_ADAPTIVE : mode) {
            case MODE_ADAPTIVE -> true;
            case MODE_RANDOM -> false;
            default -> throw new BadRequestException("mode phải là " + MODE_ADAPTIVE + " hoặc " + MODE_RANDOM);
        };
        RateLimitProperties.Bucket limit = rateLimitProperties.getQuiz();
        if (!rateLimiter.tryAcquire("ratelimit:quiz:" + username, limit.getLimit(), Duration.ofSeconds(limit.getWindowSeconds()))) {
            throw new TooManyRequestsException("Bạn tạo trắc nghiệm quá nhanh. Vui lòng đợi một chút rồi thử lại.");
        }

        Long userId = userService.getByUsername(username).getId();
        List<Kanji> pool;
        // Danh sách từ cho trước: hỏi đúng các từ đó, không chọn lại theo điểm yếu.
        List<Kanji> givenWords = null;
        if (kanjiIds != null && !kanjiIds.isEmpty()) {
            givenWords = kanjiRepository.findAllById(new LinkedHashSet<>(kanjiIds));
            if (givenWords.isEmpty()) {
                throw new BadRequestException("Không tìm thấy từ vựng nào để luyện");
            }
            pool = withLessonmates(givenWords);
        } else if (hardWords) {
            pool = kanjiRepository.findAllById(srsService.hardWordIds(userId));
            if (pool.isEmpty()) {
                throw new BadRequestException("Bạn chưa có từ khó nào để luyện riêng");
            }
        } else {
            JlptLevel normalizedLevel = Levels.optional(level);
            // Cả cấp độ = mọi bài của cấp đó (N5-01..N5-25), không theo cột jlpt_level: từ như 意味 ghi N3 nhưng cũng học ở bài N5.
            pool = tagId == null && normalizedLevel != null
                    ? kanjiRepository.findAllByTagNamePrefix(normalizedLevel + "-%")
                    : kanjiRepository.findAllByFilters(normalizedLevel, tagId);
            if (pool.isEmpty()) {
                throw new BadRequestException("Không có từ vựng nào phù hợp bộ lọc đã chọn");
            }
        }

        if (typing) {
            // Từ chỉ có kana thì gõ lại chính nó: chỉ hỏi từ có chữ Hán.
            pool = pool.stream().filter(QuizAnswerService::typeable).toList();
            if (givenWords != null) {
                givenWords = givenWords.stream().filter(QuizAnswerService::typeable).toList();
            }
            if (pool.isEmpty() || givenWords != null && givenWords.isEmpty()) {
                throw new BadRequestException("Không có từ nào có chữ Hán để gõ cách đọc");
            }
        }

        int requested = size == null || size < 1 ? DEFAULT_QUESTIONS : Math.min(size, MAX_QUESTIONS);
        RandomGenerator random = ThreadLocalRandom.current();
        // Chế độ ngẫu nhiên không cần lịch sử học: null = chọn từ và hướng hỏi ngẫu nhiên đều.
        LearnerHistory history = adaptive
                ? learnerHistoryService.load(userId, pool.stream().map(Kanji::getId).toList())
                : null;
        List<Kanji> selected;
        if (givenWords != null) {
            List<Kanji> shuffledWords = new ArrayList<>(givenWords);
            Collections.shuffle(shuffledWords, random);
            selected = shuffledWords.subList(0, Math.min(requested, shuffledWords.size()));
        } else if (history != null) {
            selected = AdaptiveQuizPlanner.selectWords(pool, requested, history, random);
        } else {
            List<Kanji> shuffledPool = new ArrayList<>(pool);
            Collections.shuffle(shuffledPool, random);
            selected = shuffledPool.subList(0, Math.min(requested, shuffledPool.size()));
        }

        Map<Long, String> newSentences = generateMissingSentences(selected, pool);

        Map<Long, Map<QuizDirection, List<PastMistake>>> pastMistakes =
                learnerHistoryService.pastMistakes(userId, selected.stream().map(Kanji::getId).toList());
        List<PlannedQuestion> plans = selected.stream()
                .map(kanji -> typing
                        ? questionBuilder.plan(kanji, TYPE_READING, List.of())
                        : plan(kanji, history, pastMistakes.getOrDefault(kanji.getId(), Map.of()), random))
                .toList();
        Map<String, List<Kanji>> existingWords = questionBuilder.existingWords(plans);

        List<QuizQuestionResponse> questions = new ArrayList<>();
        for (PlannedQuestion plan : plans) {
            Kanji kanji = plan.kanji();
            String sentence = StringUtils.hasText(kanji.getExampleSentence())
                    ? kanji.getExampleSentence()
                    : newSentences.get(kanji.getId());
            questions.add(toResponse(questionBuilder.build(plan, sentence, pool, existingWords)));
        }
        return questions;
    }

    /** {@code words} cùng các từ học chung bài với chúng - đủ để lấy đáp án nhiễu khi chỉ hỏi vài từ. */
    private List<Kanji> withLessonmates(List<Kanji> words) {
        Map<Long, Kanji> pool = new LinkedHashMap<>();
        words.forEach(word -> pool.put(word.getId(), word));
        kanjiRepository.findLessonmatesOf(pool.keySet()).forEach(word -> pool.putIfAbsent(word.getId(), word));
        return new ArrayList<>(pool.values());
    }

    /** Hướng hỏi theo điểm yếu (hoặc ngẫu nhiên đều); từ không có cách đọc thì chỉ hỏi được nghĩa. */
    private PlannedQuestion plan(Kanji kanji, LearnerHistory history, Map<QuizDirection, List<PastMistake>> pastMistakes,
                                 RandomGenerator random) {
        QuizDirection direction;
        if (!StringUtils.hasText(kanji.getReading())) {
            direction = MEANING;
        } else {
            boolean askReading = history != null
                    ? KANJI_TO_READING.equals(AdaptiveQuizPlanner.chooseDirection(kanji, history, random))
                    : random.nextBoolean();
            direction = askReading ? KANJI_TO_READING : READING_TO_KANJI;
        }
        return questionBuilder.plan(kanji, direction, pastMistakes.getOrDefault(direction, List.of()));
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

    private static QuizQuestionResponse toResponse(BuiltQuestion question) {
        Kanji kanji = question.kanji();
        PastMistake trap = question.shownTrap();
        Kanji trapWord = question.trapWord();
        // Câu gõ: không gửi cách đọc (là đáp án) và nghĩa trước khi chấm - trả trong kết quả chấm.
        boolean typing = question.direction() == TYPE_READING;
        return QuizQuestionResponse.builder()
                .kanjiId(kanji.getId())
                .direction(question.direction())
                .prompt(question.prompt())
                .sentence(question.sentence())
                .choices(question.choices())
                .correctIndex(question.correctIndex())
                .character(kanji.getCharacter())
                .reading(typing ? null : kanji.getReading())
                .meaning(typing ? null : kanji.getMeaning())
                .personalTrap(trap == null ? null : trap.answer())
                .personalTrapCount(trap == null ? 0 : (int) trap.times())
                .personalTrapReading(trapWord == null ? null : trapWord.getReading())
                .personalTrapMeaning(trapWord == null ? null : trapWord.getMeaning())
                .build();
    }
}
