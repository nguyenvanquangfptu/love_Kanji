package com.kanjimastery.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class LearningProfileRequest {

    /** N5..N1; null = chưa chọn. */
    @Pattern(regexp = "N[1-5]", message = "targetLevel phải là N5, N4, N3, N2 hoặc N1")
    private String targetLevel;

    /** Ngày thi; null = chưa có. */
    private LocalDate examDate;

    @NotNull(message = "dailyMinutes không được để trống")
    @Min(value = 5, message = "Mỗi ngày dành ít nhất 5 phút")
    @Max(value = 240, message = "Mỗi ngày tối đa 240 phút")
    private Integer dailyMinutes;

    /** Tự đặt số từ mới mỗi ngày; null = để app tính theo ngày thi và thời gian ôn. */
    @Min(value = 0, message = "Số từ mới mỗi ngày không được âm")
    @Max(value = 100, message = "Mỗi ngày tối đa 100 từ mới")
    private Integer newWordsPerDay;
}
