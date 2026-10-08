package com.kanjimastery.backend.dto;

import com.kanjimastery.backend.service.ReadingMatcher;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class QuizAnswerResponse {
    private boolean correct;
    /** Từ có nằm trong lịch ôn không - từ làm sai luôn được đưa vào. */
    private boolean inReview;
    /** Lần ôn tiếp theo; null nếu từ không nằm trong lịch ôn. */
    private LocalDateTime nextReviewAt;

    /** Câu gõ cách đọc: cách đọc đúng (câu trắc nghiệm đã có đáp án sẵn). */
    private String correctAnswer;
    /** Câu gõ cách đọc: kana server hiểu từ chuỗi người học gõ; null nếu bấm "Không nhớ". */
    private String typedKana;
    /** Câu gõ cách đọc sai gần đúng: loại lỗi để gợi ý; null nếu đúng hoặc sai hẳn. */
    private ReadingMatcher.Mistake mistake;
    /** Câu gõ cách đọc: nghĩa của từ, chỉ trả sau khi chấm. */
    private String meaning;
}
