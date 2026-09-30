package com.kanjimastery.backend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AddSrsCardsRequest {

    @NotEmpty(message = "kanjiIds không được để trống")
    @Size(max = 500, message = "Mỗi lần thêm tối đa 500 từ")
    private List<@NotNull Long> kanjiIds;
}
