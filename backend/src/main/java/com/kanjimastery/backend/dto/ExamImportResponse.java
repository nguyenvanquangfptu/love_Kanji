package com.kanjimastery.backend.dto;

import java.util.List;

/**
 * Kết quả nhập một đề. Có lỗi thì không ghi gì vào kho; {@code dryRun} chỉ kiểm tra, không ghi.
 *
 * @param questions       số câu đã ghi (hoặc sẽ ghi, khi chạy thử)
 * @param passages        số đoạn văn đã ghi (hoặc sẽ ghi)
 * @param alreadyImported số câu đã có trong kho từ lần nhập trước, được bỏ qua
 * @param errors          lỗi phải sửa trong file đề, mỗi dòng ghi câu nào (vd. "NP câu 15: ...")
 * @param warnings        điều nên xem lại nhưng không chặn việc nhập (câu chưa gắn được với từ vựng, câu trùng...)
 */
public record ExamImportResponse(String testCode, boolean dryRun, boolean imported, int questions, int passages,
                                 int alreadyImported, List<String> errors, List<String> warnings) {
}
