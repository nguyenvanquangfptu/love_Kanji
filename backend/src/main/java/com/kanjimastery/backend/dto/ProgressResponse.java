package com.kanjimastery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

/** Tiến bộ của người học - xem ProgressService. */
@Getter
@Builder
@AllArgsConstructor
public class ProgressResponse {
    /** 8 tuần gần nhất (thứ Hai đầu tuần), cũ trước, kể cả tuần không ôn. */
    private List<Week> weeks;
    /** 14 ngày học gần nhất, cũ trước. */
    private List<Day> days;
    /** Trắc nghiệm 30 ngày gần nhất theo hướng hỏi; hướng chưa làm câu nào thì không có. */
    private List<Direction> directions;
    /** Những đáp án sai chọn nhiều lần nhất trong 90 ngày. */
    private List<Confusion> confusions;
    /** FSRS dự đoán so với thực tế, 30 ngày gần nhất; null nếu chưa đủ lượt ôn để so. */
    private Calibration calibration;

    /**
     * Tỉ lệ nhớ thật: trong các lần ôn đúng hạn một thẻ đang ôn bình thường (không tính từ mới, thẻ đang học lại),
     * nhớ được bao nhiêu lần.
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class Week {
        private LocalDate weekStart;
        private long reviews;
        private long remembered;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class Day {
        private LocalDate day;
        /** Lần trả lời không phải học từ mới: ôn thẻ, luyện trắc nghiệm. */
        private long reviews;
        /** Từ mới học lần đầu. */
        private long newWords;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class Direction {
        private String direction;
        private long answers;
        private long correct;
    }

    /** Khi đến lượt ôn: FSRS đoán người học còn nhớ bao nhiêu phần trăm số từ, và thực tế nhớ được bao nhiêu. */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class Calibration {
        private long reviews;
        private double predicted;
        private double actual;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class Confusion {
        private Long kanjiId;
        private String character;
        private String reading;
        private String meaning;
        private String direction;
        private String chosenAnswer;
        private long times;
    }
}
