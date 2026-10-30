package com.kanjimastery.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Một bài đọc lẻ của phần Đọc hiểu để nhập vào kho câu thi; dạng bài (READING_SHORT...) chọn riêng khi nhập. Các câu
 * hỏi đánh số 1..n; mỗi câu dùng "cau", "cau_hoi" (câu hỏi về bài), "lua_chon", "dung", "giai_thich" như
 * {@link ExamImportRequest.Item}.
 */
public record ExamPassageImportRequest(
        @JsonProperty("cap_do") String level,
        /** Mã bài tự đặt, vd. "DH-ngan-07" - có mã thì nhập lại không tạo bài trùng; bỏ trống được. */
        @JsonProperty("ma") String code,
        @JsonProperty("tieu_de") String title,
        /** Toàn văn bài đọc: gạch chân 〔下線〕...〔/下線〕, bảng viết thành các dòng | ô | ô |. */
        @JsonProperty("noi_dung") String content,
        @JsonProperty("cau_hoi") List<ExamImportRequest.Item> questions) {
}
