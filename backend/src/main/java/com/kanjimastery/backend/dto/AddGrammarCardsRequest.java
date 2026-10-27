package com.kanjimastery.backend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AddGrammarCardsRequest {

    @NotEmpty(message = "grammarPointIds không được để trống")
    @Size(max = 200, message = "Mỗi lần thêm tối đa 200 điểm ngữ pháp")
    private List<@NotNull Long> grammarPointIds;
}
