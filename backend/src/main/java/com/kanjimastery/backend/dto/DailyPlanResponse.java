package com.kanjimastery.backend.dto;

import com.kanjimastery.backend.model.JlptLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

/** Kế hoạch ôn hôm nay của một người học - xem StudyPlanService. */
@Getter
@Builder
@AllArgsConstructor
public class DailyPlanResponse {
    /** Số phút người học dành cho ôn tập mỗi ngày. */
    private int dailyMinutes;
    /** Số giây cho một thẻ, đo từ nhịp ôn thật của người học (chưa đủ dữ liệu thì lấy mặc định). */
    private int secondsPerCard;
    /** Số thẻ ôn kịp trong {@link #dailyMinutes}. */
    private int reviewCapacity;
    /** Thẻ ôn đang đến hạn, không tính từ mới. */
    private long dueReviews;
    /** Thẻ ôn trong phiên hôm nay: nguy cơ quên cao nhất trước, tối đa {@link #reviewCapacity}. */
    private int reviewsToday;
    /** Số từ mới mỗi ngày theo kế hoạch (đã tính tới thời gian ôn, trừ khi người học tự đặt). */
    private int newPerDay;
    /** Số từ mới mỗi ngày người học muốn: tự đặt, cần để kịp ngày thi, hoặc mặc định. */
    private int newPerDayWanted;
    /** DEFAULT (chưa đặt) | CUSTOM (người học tự đặt) | EXAM (tính từ ngày thi). */
    private String newPerDaySource;
    /** Số từ mới mỗi ngày đã bị giảm vì thời gian ôn mỗi ngày không đủ cho cả lượng ôn sắp tới. */
    private boolean newPerDayLimitedByTime;
    /** Từ mới đã học trong ngày học hôm nay. */
    private long newLearnedToday;
    /** Từ mới đã đưa vào Ôn tập mà chưa học. */
    private long newWaiting;
    /** Từ mới trong phiên hôm nay. */
    private int newToday;
    private int estimatedMinutes;

    /** Người học đã đặt mục tiêu chưa. */
    private boolean goalSet;
    private JlptLevel targetLevel;
    private LocalDate examDate;
    /** Từ trong các bài từ N5 tới cấp mục tiêu mà người học chưa học lần nào; null nếu chưa chọn cấp độ. */
    private Long wordsToLearn;
    /** Số từ mới học được trung bình mỗi ngày trong 2 tuần gần đây. */
    private double recentNewPerDay;
    /** Ngày học xong mọi từ của mục tiêu nếu giữ nhịp 2 tuần gần đây; null nếu chưa có nhịp. */
    private LocalDate projectedFinish;
    /** Kịp học xong trước ngày thi 2 tuần (để còn ôn tổng) không; null nếu chưa có ngày thi hoặc chưa có nhịp. */
    private Boolean onTrack;
    /** Bài nên thêm vào Ôn tập tiếp theo, khi số từ mới đang chờ không đủ cho một ngày; null nếu không cần. */
    private NextLesson nextLesson;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class NextLesson {
        private Long tagId;
        private String name;
        /** Số từ của bài chưa có trong Ôn tập. */
        private long words;
    }
}
