package com.kanjimastery.backend.dto;

import jakarta.validation.constraints.Size;

/** Âm Hán Việt mới của một từ, mỗi chữ Hán một âm (vd. "LẠC VÀO"). */
public record HanVietRequest(@Size(max = 50, message = "hanViet tối đa 50 ký tự") String hanViet) {
}
