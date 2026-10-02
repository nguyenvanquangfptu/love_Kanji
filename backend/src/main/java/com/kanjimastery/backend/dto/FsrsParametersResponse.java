package com.kanjimastery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/** Mô hình trí nhớ FSRS của một người học: tham số chung hay đã tối ưu từ lịch sử ôn của chính họ. */
@Getter
@Builder
@AllArgsConstructor
public class FsrsParametersResponse {
    /** Đang dùng tham số tối ưu riêng (false = tham số chung của FSRS). */
    private boolean personalized;
    /** Lần tối ưu gần nhất; null nếu chưa có tham số riêng. */
    private LocalDateTime optimizedAt;
    /** Số từ đã học và ôn lại ở một ngày khác, theo mức chấm lần đầu Quên, Khó, Nhớ, Dễ. */
    private List<Integer> firstReviews;
    /** Một mức chấm cần chừng này từ mới tối ưu được. */
    private int minFirstReviews;
    /** Độ ổn định (số ngày còn nhớ 90%) sau lần học đầu theo mức chấm Quên, Khó, Nhớ, Dễ - đang dùng để xếp lịch. */
    private List<Double> initialStabilities;
    /** Như trên với tham số chung của FSRS, để so sánh. */
    private List<Double> defaultInitialStabilities;
}
