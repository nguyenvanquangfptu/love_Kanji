package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ReviewRating;
import com.kanjimastery.backend.model.ReviewSource;
import com.kanjimastery.backend.model.UserExamAnswer;
import com.kanjimastery.backend.model.UserExamAttempt;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.UserExamAnswerRepository;
import com.kanjimastery.backend.repository.UserExamAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Đưa kết quả một bài thi vào ôn tập: mỗi câu đã trả lời được ghi như một câu trắc nghiệm cho các từ nó kiểm tra
 * (nguồn EXAM, hướng hỏi = kỹ năng của câu) qua {@link SrsService#recordQuizAnswer} - sai thì từ được đưa vào ôn tập
 * (đã có thì học lại), đúng thì tính là một lần ôn nếu từ đã đến hạn. Câu bỏ trống không tính: không phân biệt được
 * không biết với hết giờ.
 */
@Service
@RequiredArgsConstructor
public class ExamDiagnosisService {

    private final UserExamAttemptRepository attemptRepository;
    private final UserExamAnswerRepository answerRepository;
    private final ExamQuestionRepository questionRepository;
    private final SrsService srsService;

    /**
     * Chạy sau khi bài thi đã chốt điểm và commit (listener AFTER_COMMIT), nên cần transaction riêng. Mỗi bài thi chỉ
     * được đưa vào một lần, kể cả khi bị gọi lại: lượt gọi đầu tiên đánh dấu {@code diagnosed_at} bằng UPDATE có điều
     * kiện, các lượt sau không làm gì.
     *
     * @return số lần trả lời đã ghi (một câu hỏi tới nhiều từ thì ghi cho từng từ)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int diagnose(Long attemptId) {
        if (attemptRepository.markDiagnosed(attemptId, LocalDateTime.now()) == 0) {
            return 0;
        }
        UserExamAttempt attempt = attemptRepository.findById(attemptId).orElseThrow();
        List<UserExamAnswer> answered = answerRepository.findByAttemptId(attemptId).stream()
                .filter(answer -> answer.getSelectedOption() != null)
                .toList();
        Map<Long, ExamQuestion> questions = questionRepository
                .findAllWithWordsByIdIn(answered.stream().map(UserExamAnswer::getQuestionId).toList()).stream()
                .collect(Collectors.toMap(ExamQuestion::getId, Function.identity()));

        int recorded = 0;
        for (UserExamAnswer answer : answered) {
            ExamQuestion question = questions.get(answer.getQuestionId());
            if (question == null) {
                continue;
            }
            boolean correct = Boolean.TRUE.equals(answer.getIsCorrect());
            // Bài thi không đo thời gian từng câu: đúng là "Nhớ", sai là "Quên".
            SrsService.Answer result = new SrsService.Answer(ReviewSource.EXAM, question.getSkill(), correct,
                    correct ? ReviewRating.GOOD : ReviewRating.AGAIN, null,
                    optionText(question, answer.getSelectedOption()));
            for (Long kanjiId : question.getKanjiIds()) {
                srsService.recordQuizAnswer(attempt.getUserId(), kanjiId, result);
                recorded++;
            }
        }
        return recorded;
    }

    private static String optionText(ExamQuestion question, String option) {
        return switch (option.toUpperCase()) {
            case "A" -> question.getOptionA();
            case "B" -> question.getOptionB();
            case "C" -> question.getOptionC();
            case "D" -> question.getOptionD();
            default -> null;
        };
    }
}
