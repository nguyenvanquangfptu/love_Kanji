package com.kanjimastery.backend.dto;

import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.ExamAttemptStatus;
import com.kanjimastery.backend.model.ExamQuestionSource;
import com.kanjimastery.backend.model.ExamSection;
import com.kanjimastery.backend.model.ExamSittingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/** Một buổi làm đề JLPT: các phần đã chọn và kết quả từng phần. */
@Getter
@Builder
@AllArgsConstructor
public class ExamSittingResponse {
    private Long sittingId;
    private JlptLevel jlptLevel;
    /** {@link com.kanjimastery.backend.model.ExamSittingStatus} */
    private ExamSittingStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    /** Nguồn câu đã chọn (vd. IMPORTED - các đề tự soạn); null = mọi câu đã duyệt. */
    private ExamQuestionSource questionSource;
    /** Các phần đã chọn, theo thứ tự làm bài. */
    private List<Section> sections;
    /** Phần làm tiếp theo; null khi đang làm dở một phần, đã làm hết, hoặc buổi thi đã kết thúc. */
    private ExamSection nextSection;
    /**
     * Điểm ước tính thang 0-60 trên các phần đã chốt điểm (tỉ lệ đúng × 60); null khi chưa phần nào xong. Chỉ để tham
     * khảo: JLPT thật quy đổi điểm theo thống kê và có điểm sàn từng phần.
     */
    private Integer estimatedScore;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class Section {
        /** {@link com.kanjimastery.backend.model.ExamSection} */
        private ExamSection name;
        /** Lượt thi của phần này; null = chưa làm. */
        private Long attemptId;
        /** {@link com.kanjimastery.backend.model.ExamAttemptStatus}; null = chưa làm. */
        private ExamAttemptStatus status;
        private Integer totalScore;
        /** Số câu của phần; null khi chưa chốt điểm. */
        private Integer totalQuestions;
        private Integer timeSpentSeconds;
        private Integer durationSeconds;
    }
}
