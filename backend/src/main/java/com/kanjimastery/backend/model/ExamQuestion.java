package com.kanjimastery.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "exam_questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "jlpt_level", nullable = false, length = 5)
    private JlptLevel jlptLevel;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "option_a", nullable = false)
    private String optionA;

    @Column(name = "option_b", nullable = false)
    private String optionB;

    @Column(name = "option_c", nullable = false)
    private String optionC;

    @Column(name = "option_d", nullable = false)
    private String optionD;

    @Column(name = "correct_option", nullable = false, length = 1)
    private String correctOption;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    /** Câu ví dụ kiểu đề JLPT; null nếu câu hỏi không có câu ví dụ. */
    @Column(columnDefinition = "TEXT")
    private String sentence;

    /** Phần được gạch chân trong {@link #sentence}. */
    @Column(length = 100)
    private String highlight;

    /** Kỹ năng câu hỏi kiểm tra, theo hướng hỏi trắc nghiệm; null nếu chưa phân loại. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private QuizDirection skill;

    /** Dạng câu trong đề JLPT; null = chỉ dùng cho thi nhanh. */
    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", length = 20)
    private JlptQuestionType questionType;

    /** Trạng thái duyệt; đổi qua các phương thức của {@link ReviewState}. */
    @Embedded
    @Builder.Default
    private ReviewState review = new ReviewState(ExamQuestionStatus.APPROVED);

    /** Đoạn văn chứa câu hỏi (問題3 文章の文法); null với câu đứng riêng. */
    @Column(name = "passage_id")
    private Long passageId;

    /** Chỗ trống 【n】 trong đoạn văn mà câu hỏi này điền vào. */
    @Column(name = "blank_no")
    private Integer blankNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private ExamQuestionSource source = ExamQuestionSource.MANUAL;

    /** Các từ vựng câu hỏi kiểm tra - làm sai thì các từ này được đưa vào ôn tập. */
    @ElementCollection
    @CollectionTable(name = "exam_question_kanji", joinColumns = @JoinColumn(name = "question_id"))
    @Column(name = "kanji_id")
    @Builder.Default
    private Set<Long> kanjiIds = new HashSet<>();

    /** Các điểm ngữ pháp câu này kiểm tra ({@link GrammarPoint}); rỗng với câu từ vựng. */
    @ElementCollection
    @CollectionTable(name = "exam_question_grammar", joinColumns = @JoinColumn(name = "question_id"))
    @Column(name = "grammar_point_id")
    @Builder.Default
    private Set<Long> grammarPointIds = new HashSet<>();

    public ExamQuestionStatus getStatus() {
        return review.getStatus();
    }

    public ExamQuestionFlag getFlag() {
        return review.getFlag();
    }

    public String getReviewNote() {
        return review.getReviewNote();
    }

    public LocalDateTime getReviewedAt() {
        return review.getReviewedAt();
    }
}
