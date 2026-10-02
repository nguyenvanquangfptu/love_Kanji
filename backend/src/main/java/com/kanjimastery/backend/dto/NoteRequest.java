package com.kanjimastery.backend.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NoteRequest {

    /** Để trống là xoá ghi chú. */
    @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự")
    private String note;
}
