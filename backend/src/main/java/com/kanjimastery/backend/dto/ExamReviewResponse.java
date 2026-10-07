package com.kanjimastery.backend.dto;

import com.kanjimastery.backend.model.ExamSection;
import com.kanjimastery.backend.model.ExamAttemptStatus;
import com.kanjimastery.backend.model.QuizDirection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class ExamReviewResponse {
    private Long attemptId;
    private String jlptLevel;
    private ExamAttemptStatus status;
    private Integer totalScore;
    private Integer totalQuestions;
    private Integer timeSpentSeconds;
    private List<QuestionReviewItem> questions;
    /** Điểm theo kỹ năng, theo thứ tự đọc - viết - nghĩa; kỹ năng không có câu nào trong bài thì không có. */
    private List<SkillScore> skills;
    /** Từ vựng của các câu làm sai (không tính câu bỏ trống), theo thứ tự câu hỏi, không lặp. */
    private List<Word> wrongWords;
    /** Kết quả bài thi đã được đưa vào ôn tập: từ của câu sai đã nằm trong lịch ôn. */
    private boolean addedToReview;
    /** Buổi làm đề JLPT và phần của lượt thi này; null với thi nhanh. */
    private Long sittingId;
    private ExamSection section;
    /** Điểm theo từng 問題 của phần đề JLPT, theo thứ tự trong đề; rỗng với thi nhanh. */
    private List<MondaiScore> mondai;
    /** Đoạn văn của các câu 文章の文法 trong bài; rỗng nếu không có. */
    private List<ExamPassageResponse> passages;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class Word {
        private Long kanjiId;
        private String character;
        private String reading;
        private String meaning;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class MondaiScore {
        /** Số thứ tự trong đề thật (問題1, 問題2...). */
        private int number;
        /** {@link com.kanjimastery.backend.model.JlptQuestionType} */
        private String type;
        private int correct;
        private int total;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class SkillScore {
        /** Như hướng hỏi trắc nghiệm: KANJI_TO_READING (đọc), READING_TO_KANJI (viết), MEANING (nghĩa). */
        private QuizDirection skill;
        private int correct;
        private int total;
    }
}
