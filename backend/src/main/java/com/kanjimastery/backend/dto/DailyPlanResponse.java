package com.kanjimastery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

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
    /** Số từ mới mỗi ngày theo kế hoạch. */
    private int newPerDay;
    /** Số từ mới mỗi ngày đã bị giảm vì thời gian ôn mỗi ngày không đủ cho cả lượng ôn sắp tới. */
    private boolean newPerDayLimitedByTime;
    /** Từ mới đã học trong ngày học hôm nay. */
    private long newLearnedToday;
    /** Từ mới đã đưa vào Ôn tập mà chưa học. */
    private long newWaiting;
    /** Từ mới trong phiên hôm nay. */
    private int newToday;
    private int estimatedMinutes;
}
