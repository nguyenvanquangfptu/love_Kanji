package com.kanjimastery.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Từ vựng của một bài để nhập vào kho từ. Tên trường JSON là tiếng Việt cho dễ soạn tay, giống file nhập đề.
 *
 * @param lesson tên bài (tag), vd. "N2-01" - chưa có thì được tạo
 * @param level  cấp độ JLPT của các từ mới thêm, vd. "N2" (từ đã có trong kho giữ cấp độ cũ)
 */
public record VocabularyImportRequest(
        @JsonProperty("bai") String lesson,
        @JsonProperty("cap_do") String level,
        @JsonProperty("tu_vung") List<Word> words) {

    /**
     * Một từ.
     *
     * @param word            cách viết, động từ ở dạng từ điển; tính từ đuôi な có thể ghi kèm な (được bỏ khi nhập)
     * @param reading         cách đọc hiragana; để trống với từ chỉ viết bằng kana
     * @param hanViet         âm Hán Việt, mỗi chữ Hán một âm (vd. "NHÂN SINH")
     * @param meaning         nghĩa tiếng Việt
     * @param partOfSpeech    từ loại, vd. 名詞, 動詞, 形容詞 - chỉ dùng để nhận tính từ đuôi な
     * @param exampleSentence câu ví dụ (không bắt buộc)
     * @param strokeCount     số nét - chỉ cần khi từ có chữ Hán chưa có trong bảng số nét
     * @param forceNew        true: luôn thêm dòng mới, kể cả khi kho đã có từ cùng cách viết, cách đọc (khác nghĩa)
     */
    public record Word(
            @JsonProperty("tu") String word,
            @JsonProperty("doc") String reading,
            @JsonProperty("han_viet") String hanViet,
            @JsonProperty("nghia") String meaning,
            @JsonProperty("loai") String partOfSpeech,
            @JsonProperty("cau_vi_du") String exampleSentence,
            @JsonProperty("so_net") Integer strokeCount,
            @JsonProperty("tao_moi") Boolean forceNew) {
    }
}
