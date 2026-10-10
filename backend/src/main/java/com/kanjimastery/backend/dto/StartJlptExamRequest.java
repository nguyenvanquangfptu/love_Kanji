package com.kanjimastery.backend.dto;

import com.kanjimastery.backend.model.ExamQuestionSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class StartJlptExamRequest {

    @NotBlank(message = "jlptLevel không được để trống")
    private String jlptLevel;

    /** Các phần muốn làm ({@link com.kanjimastery.backend.model.ExamSection}); luôn làm theo thứ tự của đề thật. */
    @NotEmpty(message = "Chọn ít nhất một phần thi")
    private List<String> sections;

    /** Chỉ lấy câu của nguồn này (vd. IMPORTED - các đề tự soạn); bỏ trống = mọi câu đã duyệt. */
    private ExamQuestionSource source;
}
