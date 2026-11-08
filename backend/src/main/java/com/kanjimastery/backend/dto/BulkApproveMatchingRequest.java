package com.kanjimastery.backend.dto;

import com.kanjimastery.backend.model.ExamSection;
import com.kanjimastery.backend.model.JlptQuestionType;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Duyệt mọi câu / đoạn văn chờ duyệt khớp bộ lọc (không chỉ trang đang xem). Bắt buộc có cấp độ để không duyệt nhầm
 * cả ngân hàng câu; các trường còn lại null = không lọc.
 */
@Getter
@Setter
public class BulkApproveMatchingRequest {

    @NotBlank(message = "Chưa chọn cấp độ")
    private String level;

    private JlptQuestionType type;

    /** Chỉ dùng khi duyệt đoạn văn: GRAMMAR (文章の文法) hoặc READING (bài đọc). */
    private ExamSection section;

    /** Mã đề tự soạn (vd. N3-05). */
    private String test;

    /** Chỉ dùng khi duyệt câu lẻ: lọc theo điểm ngữ pháp. */
    private Long grammarPointId;
}
