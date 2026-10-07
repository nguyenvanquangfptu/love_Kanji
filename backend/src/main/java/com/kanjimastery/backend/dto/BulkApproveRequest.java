package com.kanjimastery.backend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** Duyệt một lượt các câu người duyệt đã đọc trên trang. */
@Getter
@Setter
public class BulkApproveRequest {

    @NotEmpty(message = "Chưa chọn câu nào")
    @Size(max = 100, message = "Tối đa 100 câu mỗi lượt")
    private List<@NotNull Long> ids;
}
