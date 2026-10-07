package com.kanjimastery.backend.dto;

import com.kanjimastery.backend.model.SchedulerType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
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

    /** SM2 hoặc FSRS; null = giữ SM2. */
    private SchedulerType scheduler;

    /** Tỉ lệ nhớ mong muốn khi dùng FSRS (0,70 - 0,97); null = 0,90. */
    @DecimalMin(value = "0.70", message = "Tỉ lệ nhớ mong muốn tối thiểu 70%")
    @DecimalMax(value = "0.97", message = "Tỉ lệ nhớ mong muốn tối đa 97%")
    private BigDecimal desiredRetention;
}
