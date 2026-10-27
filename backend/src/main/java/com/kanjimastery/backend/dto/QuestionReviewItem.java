package com.kanjimastery.backend.dto;

import com.kanjimastery.backend.model.GrammarPoint;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.model.QuizDirection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class QuestionReviewItem {
    private Long questionId;
    private String questionText;
    /** Câu ví dụ kiểu đề JLPT, gạch chân {@code highlight}; null nếu không có. */
    private String sentence;
    /** {@link #sentence} chia đoạn kèm furigana (xem lại sau khi nộp nên có cả furigana của phần gạch chân); null nếu không có câu. */
    private List<RubySegment> sentenceRuby;
    private String highlight;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    private String correctOption;
    private String selectedOption;
    private boolean correct;
    private String explanation;
    /** Kỹ năng câu hỏi kiểm tra; null nếu chưa phân loại. */
    private QuizDirection skill;
    /** Dạng câu JLPT ({@link com.kanjimastery.backend.model.JlptQuestionType}); null với câu chỉ dùng cho thi nhanh. */
    private JlptQuestionType questionType;
    /** Câu điền vào chỗ trống 【blankNo】 của đoạn văn passageId (文章の文法); null với câu đứng riêng. */
    private Long passageId;
    private Integer blankNo;
    /** Các điểm ngữ pháp câu này kiểm tra; rỗng với câu từ vựng. */
    private List<Grammar> grammarPoints;
    /** Người học đang xem đã báo lỗi câu này. */
    private boolean reported;

    /**
     * Điểm ngữ pháp kèm cách nối và giải thích, để người học đọc lại ngay khi xem câu.
     *
     * @param connection    cách nối; null nếu chưa có
     * @param explanationVi giải thích và ví dụ, các dòng cách nhau bằng xuống dòng; null nếu chưa có
     */
    public record Grammar(Long id, String pattern, String meaningVi, String connection, String explanationVi) {

        public static Grammar from(GrammarPoint point) {
            return new Grammar(point.getId(), point.getPattern(), point.getMeaningVi(), point.getConnection(),
                    point.getExplanationVi());
        }
    }
}
