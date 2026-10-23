package com.kanjimastery.backend.dto;

import java.util.List;

/**
 * Phiên ôn ngữ pháp hôm nay: các điểm đến hạn trước, rồi các điểm mới trong giới hạn của ngày.
 *
 * @param dueCount         số điểm đã đến hạn (có thể nhiều hơn số thẻ trong phiên)
 * @param newLeftToday     số điểm mới còn được học hôm nay
 * @param newWaiting       số điểm đã thêm mà chưa học lần nào
 */
public record GrammarSessionResponse(List<GrammarCardResponse> cards, long dueCount, int newLeftToday,
                                     long newWaiting) {
}
