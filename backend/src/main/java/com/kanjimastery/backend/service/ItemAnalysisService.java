package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionFlag;
import com.kanjimastery.backend.model.ExamQuestionStats;
import com.kanjimastery.backend.model.ExamQuestionStatus;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.ExamQuestionStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Phân tích câu hỏi (item analysis) từ kết quả thi thật: với mỗi câu thi JLPT, tính số lượt trả lời, tỉ lệ đúng và độ
 * phân biệt trên các lượt làm từ lần duyệt câu gần nhất, lưu lại để người duyệt xem. Câu đã duyệt có đủ
 * {@value #MIN_FLAG_RESPONSES} lượt mà người làm tốt các câu khác lại hay sai câu này (độ phân biệt âm), hoặc gần như
 * không ai đúng và không phân biệt được người giỏi, thì gắn cờ - thường là sai đáp án hoặc có hai đáp án. Câu vẫn ở
 * trong đề cho tới khi người duyệt quyết định; duyệt lại thì thống kê tính lại từ đầu.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItemAnalysisService {

    /** Lưu thống kê từ chừng ấy lượt trả lời. */
    public static final int MIN_STATS_RESPONSES = 5;
    /** Gắn cờ chỉ khi đủ chừng ấy lượt - ít hơn thì độ phân biệt còn nhiễu. */
    public static final int MIN_FLAG_RESPONSES = 30;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ExamQuestionStatsRepository statsRepository;
    private final ExamQuestionRepository questionRepository;
    private final Clock clock;

    /**
     * @param analyzed số câu có thống kê
     * @param flagged  số câu vừa bị gắn cờ ở lần phân tích này
     */
    public record Summary(int analyzed, int flagged) {
    }

    @Transactional
    public Summary analyze() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<ExamQuestionStats> stats = statsRepository.computeAll(MIN_STATS_RESPONSES).stream()
                .map(row -> new ExamQuestionStats(row.getQuestionId(), row.getResponses().intValue(),
                        row.getCorrectRate(), row.getDiscrimination(), now))
                .toList();
        // Thay toàn bộ: thống kê của câu vừa được duyệt lại phải mất đi, tính lại từ các lượt làm sau đó.
        statsRepository.deleteAllInBatch();
        statsRepository.saveAll(stats);

        List<ExamQuestionStats> suspicious = stats.stream()
                .filter(row -> suspicious(row.getResponses(), row.getCorrectRate(), row.getDiscrimination()))
                .toList();
        Map<Long, ExamQuestion> questions = questionRepository.findAllById(suspicious.stream()
                        .map(ExamQuestionStats::getQuestionId).toList()).stream()
                .collect(Collectors.toMap(ExamQuestion::getId, Function.identity()));
        int flagged = 0;
        for (ExamQuestionStats row : suspicious) {
            ExamQuestion question = questions.get(row.getQuestionId());
            // Câu đang có cờ (người duyệt chưa xem) hoặc không còn trong đề thì để yên.
            if (question == null || question.getFlag() != null
                    || !ExamQuestionStatus.APPROVED.equals(question.getStatus())) {
                continue;
            }
            question.getReview().raise(ExamQuestionFlag.STATS, describe(row, now));
            flagged++;
        }
        log.info("Phân tích câu hỏi: {} câu có thống kê, gắn cờ {} câu.", stats.size(), flagged);
        return new Summary(stats.size(), flagged);
    }

    /** Câu đáng ngờ: độ phân biệt âm, hoặc rất ít người đúng mà không phân biệt được người giỏi. */
    static boolean suspicious(int responses, double correctRate, Double discrimination) {
        if (responses < MIN_FLAG_RESPONSES || discrimination == null) {
            return false;
        }
        return discrimination < 0 || correctRate < 0.25 && discrimination < 0.1;
    }

    private static String describe(ExamQuestionStats row, LocalDateTime at) {
        String reason = row.getDiscrimination() < 0
                ? "người làm tốt các câu khác lại hay sai câu này"
                : "rất ít người làm đúng và câu không phân biệt được người giỏi";
        return ("Phân tích câu %s: %d lượt làm từ lần duyệt gần nhất, đúng %d%%, độ phân biệt %.2f - %s "
                + "(nghi sai đáp án hoặc có hai đáp án).")
                .formatted(at.format(DATE), row.getResponses(), Math.round(row.getCorrectRate() * 100),
                        row.getDiscrimination(), reason);
    }
}
