package com.kanjimastery.backend.dto;

import com.kanjimastery.backend.model.QuizDirection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizQuestionResponse {
    private Long kanjiId;
    private QuizDirection direction;
    /** Từ cần hỏi: dạng Kanji (KANJI_TO_READING, MEANING) hoặc hiragana (READING_TO_KANJI). */
    private String prompt;
    /** Câu ví dụ kiểu đề JLPT có chứa nguyên văn {@link #prompt} (để gạch chân); null nếu chưa có câu. */
    private String sentence;
    private List<String> choices;
    private int correctIndex;
    /** Hiển thị sau khi trả lời để người học ôn thêm. */
    private String character;
    private String reading;
    private String meaning;
    /** Đáp án sai người học từng chọn nhiều nhất cho từ này theo hướng hỏi này, có mặt trong {@link #choices}; null nếu chưa từng nhầm. */
    private String personalTrap;
    /** Số lần đã chọn {@link #personalTrap}. */
    private int personalTrapCount;
    /** Khi {@link #personalTrap} là cách viết của một từ có thật: cách đọc và nghĩa của từ đó, để so sánh. */
    private String personalTrapReading;
    private String personalTrapMeaning;
}
