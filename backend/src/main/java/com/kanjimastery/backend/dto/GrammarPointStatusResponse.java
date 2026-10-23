package com.kanjimastery.backend.dto;

import java.time.LocalDateTime;

/**
 * Một điểm ngữ pháp trong danh sách cho người học, kèm trạng thái ôn của chính người đó.
 *
 * @param nextReviewAt null nếu chưa có trong lịch ôn
 */
public record GrammarPointStatusResponse(Long id, String lesson, String pattern, String connection, String meaningVi,
                                         String explanationVi, boolean inReview, LocalDateTime nextReviewAt) {
}
