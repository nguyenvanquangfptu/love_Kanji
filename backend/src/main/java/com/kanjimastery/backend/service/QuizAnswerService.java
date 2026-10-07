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
import java.util.Optional;

/**
 * Nhận kết quả từng câu trắc nghiệm: server tự chấm (không tin kết quả do client gửi), suy mức độ nhớ từ
 * thời gian trả lời rồi chuyển cho {@link SrsService#recordQuizAnswer} ghi lại và cập nhật lịch ôn.
 */
@Service
@RequiredArgsConstructor
public class QuizAnswerService {

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

    /** Đáp án đúng giống hệt cách {@link QuizService} dựng câu hỏi; null nếu từ không hỏi được theo hướng đó. */
    static String correctAnswer(Kanji kanji, QuizDirection direction) {
        return switch (direction) {
            case KANJI_TO_READING -> StringUtils.hasText(kanji.getReading()) ? kanji.getReading() : null;
            case READING_TO_KANJI -> StringUtils.hasText(kanji.getReading()) ? kanji.getCharacter() : null;
            case MEANING -> kanji.getMeaning();
        };
    }
}
