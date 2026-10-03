package com.kanjimastery.backend.dto;

/**
 * Một dòng của bảng xếp hạng đề JLPT: buổi thi làm đủ các phần tốt nhất của người học.
 *
 * @param rank             hạng (1 = cao nhất); null khi người học chưa có trên bảng
 * @param estimatedScore   điểm ước tính thang 0-60
 * @param correct          số câu đúng trên {@code total} câu của buổi thi
 * @param timeSpentSeconds tổng thời gian làm các phần
 */
public record JlptLeaderboardEntryResponse(Integer rank, Long userId, String username, Integer estimatedScore,
                                           Integer correct, Integer total, Integer timeSpentSeconds) {
}
