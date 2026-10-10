package com.kanjimastery.backend.dto;

import com.kanjimastery.backend.model.QuizDirection;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QuizAnswerRequest {

    @NotNull(message = "kanjiId không được để trống")
    private Long kanjiId;

    @NotNull(message = "direction không được để trống")
    private QuizDirection direction;

    /** Đáp án đã chọn; với câu gõ cách đọc là chuỗi người học gõ (romaji hoặc kana). Bỏ trống khi {@link #gaveUp}. */
    @Size(max = 1000, message = "chosenAnswer quá dài")
    private String chosenAnswer;

    /** Câu gõ cách đọc: người học bấm "Không nhớ" - tính là trả lời sai. */
    private boolean gaveUp;

    /** Thời gian từ lúc hiện câu hỏi tới lúc chọn đáp án (mili giây), không bắt buộc. */
    private Integer responseMs;
}
