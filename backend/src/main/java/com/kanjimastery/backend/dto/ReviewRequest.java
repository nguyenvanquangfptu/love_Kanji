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

    @NotNull(message = "quality không được để trống")
    @Min(value = 0, message = "quality phải từ 0 đến 5")
    @Max(value = 5, message = "quality phải từ 0 đến 5")
    private Integer quality;
}
