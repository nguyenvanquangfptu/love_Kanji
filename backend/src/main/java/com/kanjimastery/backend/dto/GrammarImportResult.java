package com.kanjimastery.backend.dto;

import java.util.List;

/**
 * Kết quả nhập danh sách ngữ pháp từ CSV.
 *
 * @param created   số điểm ngữ pháp mới
 * @param updated   số điểm đã có (cùng cấp độ + mẫu) được cập nhật nội dung
 * @param unchanged số điểm đã có, nội dung giống hệt
 * @param errors    các dòng bị bỏ qua, kèm lý do (số dòng tính từ 1)
 */
public record GrammarImportResult(int created, int updated, int unchanged, List<String> errors) {
}
