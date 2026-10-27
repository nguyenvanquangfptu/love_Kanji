package com.kanjimastery.backend.dto;

/**
 * Chuỗi ngày học liên tiếp. Một ngày có học = có ít nhất một lần trả lời (ôn thẻ, trắc nghiệm, bài thi) trong ngày học
 * (bắt đầu 4 giờ sáng giờ Việt Nam).
 *
 * @param current      số ngày liên tiếp tới hôm nay; hôm nay chưa học thì tính tới hôm qua (chuỗi chưa đứt khi ngày
 *                     chưa hết)
 * @param longest      chuỗi dài nhất từng có
 * @param studiedToday hôm nay đã học chưa
 */
public record StreakResponse(int current, int longest, boolean studiedToday) {
}
