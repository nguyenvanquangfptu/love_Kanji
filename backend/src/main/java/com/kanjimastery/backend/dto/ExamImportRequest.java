package com.kanjimastery.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Một đề tự soạn (phần Từ vựng và Ngữ pháp) để nhập vào kho câu thi. Tên trường JSON là tiếng Việt cho dễ soạn tay;
 * các trường không dùng tới của một dạng câu thì bỏ trống. Số câu ({@code cau}) đánh liên tục trong mỗi phần như đề
 * thật: Từ vựng 1..35, Ngữ pháp 1..23 (với N3).
 */
public record ExamImportRequest(
        /** Mã đề, vd. "N3-05"; ghép với số câu thành mã câu đề gốc (N3-05/TV/12). */
        @JsonProperty("de") String testCode,
        @JsonProperty("cap_do") String level,
        @JsonProperty("tu_vung") VocabularyPart vocabulary,
        @JsonProperty("ngu_phap") GrammarPart grammar) {

    public record VocabularyPart(
            /** 問題1 漢字読み. */
            @JsonProperty("kanji_doc") List<Item> kanjiReading,
            /** 問題2 表記. */
            @JsonProperty("kanji_viet") List<Item> orthography,
            /** 問題3 文脈規定. */
            @JsonProperty("dien_tu") List<Item> context,
            /** 問題4 言い換え類義. */
            @JsonProperty("dong_nghia") List<Item> paraphrase,
            /** 問題5 用法. */
            @JsonProperty("cach_dung") List<Item> usage) {
    }

    public record GrammarPart(
            /** 問題1 文法形式の判断. */
            @JsonProperty("chon_mau") List<Item> grammarForm,
            /** 問題2 文の組み立て. */
            @JsonProperty("sap_xep") List<Item> sentenceOrder,
            /** 問題3 文章の文法. */
            @JsonProperty("doan_van") Passage passage) {
    }

    /** Một câu hỏi; mỗi dạng câu dùng một phần các trường (xem ExamImportService). */
    public record Item(
            @JsonProperty("cau") Integer number,
            /** Câu hỏi; câu điền từ có một ô （　　）. */
            @JsonProperty("cau_hoi") String sentence,
            /** Phần gạch chân (漢字読み, 表記, 言い換え), phải nằm trong câu hỏi. */
            @JsonProperty("gach_chan") String highlight,
            /** Từ được hỏi cách dùng (用法). */
            @JsonProperty("tu_khoa") String keyword,
            /** Câu sắp xếp: phần đứng trước và sau 4 vế. */
            @JsonProperty("truoc") String before,
            @JsonProperty("sau") String after,
            @JsonProperty("lua_chon") List<String> options,
            /** Lựa chọn đúng, 1-4. */
            @JsonProperty("dung") Integer answer,
            /** Câu sắp xếp: số thứ tự (1-4) của các lựa chọn theo đúng thứ tự trong câu, vd. [2, 4, 3, 1]. */
            @JsonProperty("thu_tu_dung") List<Integer> order,
            /** Câu sắp xếp: ô có dấu ★ (1-4). */
            @JsonProperty("vi_tri_sao") Integer star,
            /** Từ vựng câu hỏi kiểm tra, dạng từ điển (vd. 許す) - để gắn với kho từ khi không tự nhận ra được. */
            @JsonProperty("tu") String word,
            /** Mẫu ngữ pháp câu hỏi kiểm tra, viết như trong danh sách ngữ pháp (vd. 〜っこない). */
            @JsonProperty("ngu_phap") String grammarPattern,
            @JsonProperty("giai_thich") String explanation) {
    }

    /** Đoạn văn có các chỗ trống đánh theo số câu trong đề: 【19】... (câu điền hai chỗ: 【20a】 và 【20b】). */
    public record Passage(
            @JsonProperty("tieu_de") String title,
            @JsonProperty("noi_dung") String content,
            @JsonProperty("cau_hoi") List<Item> questions) {
    }
}
