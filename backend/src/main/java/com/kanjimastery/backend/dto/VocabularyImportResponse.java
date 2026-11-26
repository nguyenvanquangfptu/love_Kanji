package com.kanjimastery.backend.dto;

import java.util.List;

/**
 * Kết quả nhập một bài từ vựng. Có lỗi thì không ghi gì; {@code dryRun} chỉ kiểm tra.
 *
 * @param lesson          tên bài
 * @param created         số từ mới thêm vào kho (hoặc sẽ thêm)
 * @param linked          số từ đã có trong kho được gắn thêm vào bài
 * @param alreadyInLesson số từ đã nằm trong bài từ trước - bỏ qua, nên nhập lại một file không tạo trùng
 * @param errors          lỗi phải sửa trong file, mỗi dòng ghi từ nào (vd. "Từ 3 「好む」: ...")
 * @param warnings        điều nên xem lại nhưng không chặn việc nhập (từ đồng tự, từ kana trùng chữ...)
 */
public record VocabularyImportResponse(String lesson, boolean dryRun, boolean imported, int created, int linked,
                                       int alreadyInLesson, List<String> errors, List<String> warnings) {
}
