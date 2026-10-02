package com.kanjimastery.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewRequest {

    @NotNull(message = "kanjiId không được để trống")
    private Long kanjiId;

    /** 1 Quên, 2 Khó, 3 Nhớ, 4 Dễ - xem {@link com.kanjimastery.backend.model.ReviewRating}. */
    @NotNull(message = "rating không được để trống")
    @Min(value = 1, message = "rating phải từ 1 đến 4")
    @Max(value = 4, message = "rating phải từ 1 đến 4")
    private Integer rating;

    /** Thời gian từ lúc hiện thẻ tới lúc lật thẻ (mili giây), không bắt buộc. */
    private Integer responseMs;
}
