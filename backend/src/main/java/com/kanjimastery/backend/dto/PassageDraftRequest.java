package com.kanjimastery.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Nhờ AI viết nháp một đoạn văn 文章の文法. */
@Getter
@Setter
public class PassageDraftRequest {

    @NotBlank(message = "Thiếu cấp độ")
    private String level;
}
