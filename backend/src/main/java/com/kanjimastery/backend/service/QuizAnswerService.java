package com.kanjimastery.backend.service;

import com.kanjimastery.backend.config.RateLimitProperties;
import com.kanjimastery.backend.dto.QuizAnswerRequest;
import com.kanjimastery.backend.dto.QuizAnswerResponse;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.exception.ResourceNotFoundException;
import com.kanjimastery.backend.exception.TooManyRequestsException;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.model.QuizDirection;
import com.kanjimastery.backend.model.ReviewRating;
import com.kanjimastery.backend.model.ReviewSource;
import com.kanjimastery.backend.repository.KanjiRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Nhận kết quả từng câu trắc nghiệm: server tự chấm (không tin kết quả do client gửi), suy mức độ nhớ từ
 * thời gian trả lời rồi chuyển cho {@link SrsService#recordQuizAnswer} ghi lại và cập nhật lịch ôn.
 */
@Service
@RequiredArgsConstructor
public class QuizAnswerService {

    /** Cách đọc dài nhất trong kho chưa tới 20 kana; quá dài là gõ nhầm. */
    static final int MAX_TYPED_LENGTH = 100;

    private final KanjiRepository kanjiRepository;
    private final UserService userService;
    private final SrsService srsService;
    private final ResponseTimeRater responseTimeRater;
    private final RateLimiterService rateLimiter;
    private final RateLimitProperties rateLimitProperties;

    public QuizAnswerResponse submit(String username, QuizAnswerRequest request) {
        RateLimitProperties.Bucket limit = rateLimitProperties.getQuizAnswer();
        if (!rateLimiter.tryAcquire("ratelimit:quiz-answer:" + username, limit.getLimit(),
                Duration.ofSeconds(limit.getWindowSeconds()))) {
            throw new TooManyRequestsException("Bạn gửi đáp án quá nhanh. Vui lòng đợi một chút rồi thử lại.");
        }

        Kanji kanji = kanjiRepository.findById(request.getKanjiId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Kanji với id: " + request.getKanjiId()));
        QuizDirection direction = request.getDirection();
        if (direction == QuizDirection.TYPE_READING) {
            return submitTyped(username, kanji, request);
        }
        if (!StringUtils.hasText(request.getChosenAnswer())) {
            throw new BadRequestException("chosenAnswer không được để trống");
        }
        String correctAnswer = correctAnswer(kanji, direction);
        if (correctAnswer == null) {
            throw new BadRequestException("Từ '" + kanji.getCharacter() + "' không có cách đọc để hỏi theo hướng " + direction);
        }

        Long userId = userService.getByUsername(username).getId();
        boolean correct = correctAnswer.equals(request.getChosenAnswer());
        Integer responseMs = ResponseTimeRater.normalize(request.getResponseMs());
        ReviewRating rating = correct ? responseTimeRater.rateCorrectAnswer(userId, direction, responseMs) : ReviewRating.AGAIN;

        Optional<LocalDateTime> nextReviewAt = srsService.recordQuizAnswer(userId, kanji.getId(), new SrsService.Answer(
                ReviewSource.QUIZ, direction, correct, rating, responseMs, request.getChosenAnswer()));

        return QuizAnswerResponse.builder()
                .correct(correct)
                .inReview(nextReviewAt.isPresent())
                .nextReviewAt(nextReviewAt.orElse(null))
                .build();
    }

    /**
     * Câu gõ cách đọc: chấm bằng {@link ReadingMatcher} với cách đọc của mọi mục cùng chữ trong kho. Nhật ký ghi kana
     * server hiểu được (để thống kê "hay gõ nhầm thành gì"); "Không nhớ" tính là sai.
     */
    private QuizAnswerResponse submitTyped(String username, Kanji kanji, QuizAnswerRequest request) {
        if (!typeable(kanji)) {
            throw new BadRequestException("Từ '" + kanji.getCharacter() + "' không có chữ Hán và cách đọc để gõ");
        }
        ReadingMatcher.Result result = null;
        if (!request.isGaveUp()) {
            String typed = request.getChosenAnswer();
            if (!StringUtils.hasText(typed) || typed.length() > MAX_TYPED_LENGTH) {
                throw new BadRequestException("Hãy gõ cách đọc (tối đa " + MAX_TYPED_LENGTH + " ký tự)");
            }
            List<String> readings = kanjiRepository.findAllByCharacter(kanji.getCharacter()).stream()
                    .map(Kanji::getReading)
                    .filter(StringUtils::hasText)
                    .toList();
            result = ReadingMatcher.match(typed, readings).orElseThrow(() -> new BadRequestException(
                    "Không đọc được cách đọc vừa gõ - gõ bằng romaji (vd. shusshin) hoặc kana"));
        }

        Long userId = userService.getByUsername(username).getId();
        boolean correct = result != null && result.correct();
        Integer responseMs = ResponseTimeRater.normalize(request.getResponseMs());
        ReviewRating rating = correct
                ? responseTimeRater.rateCorrectAnswer(userId, QuizDirection.TYPE_READING, responseMs)
                : ReviewRating.AGAIN;
        String typedKana = result == null ? null : result.typedKana();
        Optional<LocalDateTime> nextReviewAt = srsService.recordQuizAnswer(userId, kanji.getId(), new SrsService.Answer(
                ReviewSource.QUIZ, QuizDirection.TYPE_READING, correct, rating, responseMs, typedKana));

        return QuizAnswerResponse.builder()
                .correct(correct)
                .inReview(nextReviewAt.isPresent())
                .nextReviewAt(nextReviewAt.orElse(null))
                .correctAnswer(ReadingMatcher.displayReading(kanji.getReading()))
                .typedKana(typedKana)
                .mistake(result == null ? null : result.mistake())
                .meaning(kanji.getMeaning())
                .build();
    }

    /** Hỏi gõ cách đọc được: từ có chữ Hán và có cách đọc (từ chỉ có kana thì gõ lại chính nó). */
    static boolean typeable(Kanji kanji) {
        return StringUtils.hasText(kanji.getReading())
                && kanji.getCharacter().codePoints().anyMatch(QuizDistractorGenerator::isKanji);
    }

    /** Đáp án đúng giống hệt cách {@link QuizService} dựng câu hỏi; null nếu từ không hỏi được theo hướng đó. */
    static String correctAnswer(Kanji kanji, QuizDirection direction) {
        return switch (direction) {
            case KANJI_TO_READING -> StringUtils.hasText(kanji.getReading()) ? kanji.getReading() : null;
            case READING_TO_KANJI -> StringUtils.hasText(kanji.getReading()) ? kanji.getCharacter() : null;
            case MEANING -> kanji.getMeaning();
            case TYPE_READING -> typeable(kanji) ? ReadingMatcher.displayReading(kanji.getReading()) : null;
        };
    }
}
