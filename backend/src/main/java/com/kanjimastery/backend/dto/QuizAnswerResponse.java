package com.kanjimastery.backend.dto;

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
}
