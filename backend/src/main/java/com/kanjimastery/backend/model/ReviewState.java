package com.kanjimastery.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Trạng thái duyệt của một câu thi hoặc một đoạn văn: trạng thái, cảnh báo cho người duyệt, ghi chú và lúc duyệt. Chỉ đổi
 * qua các phương thức bên dưới để quy tắc (một cờ nặng nhất, ghi chú nối thêm, duyệt thì xoá cờ) nằm một chỗ.
 */
@Embeddable
@Getter
public class ReviewState {

    /** Chỉ câu đã duyệt mới được lấy vào đề; câu của một đoạn văn luôn cùng trạng thái với đoạn. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ExamQuestionStatus status;

    /** Cảnh báo cho người duyệt; null = không có. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ExamQuestionFlag flag;

    /** Lý do loại, chi tiết cảnh báo của bước kiểm tra tự động, hoặc ghi chú của người duyệt. */
    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    protected ReviewState() {
        // Cho JPA.
    }

    public ReviewState(ExamQuestionStatus status) {
        this(status, null, null, null);
    }

    public ReviewState(ExamQuestionStatus status, ExamQuestionFlag flag, String reviewNote, LocalDateTime reviewedAt) {
        this.status = status;
        this.flag = flag;
        this.reviewNote = reviewNote;
        this.reviewedAt = reviewedAt;
    }

    /** Người duyệt chuyển trạng thái; duyệt thì cảnh báo coi như đã được xem. */
    public void decide(ExamQuestionStatus status, LocalDateTime now) {
        this.status = status;
        this.reviewedAt = now;
        if (status == ExamQuestionStatus.APPROVED) {
            this.flag = null;
        }
    }

    /** Đổi trạng thái không phải do người duyệt (máy loại nháp hỏng, tự rút khỏi đề): không ghi lúc duyệt. */
    public void moveTo(ExamQuestionStatus status) {
        this.status = status;
    }

    /** Gắn cờ nếu nặng hơn cờ đang có (một câu chỉ giữ cờ nặng nhất), và ghi thêm chi tiết nếu có. */
    public void raise(ExamQuestionFlag flag, String detail) {
        if (this.flag == null || flag.severity() < this.flag.severity()) {
            this.flag = flag;
        }
        if (detail != null) {
            addNote(detail);
        }
    }

    /** Gắn cờ thay cho cờ đang có, bất kể nặng nhẹ (người học báo lỗi: câu đã bị rút khỏi đề), và ghi thêm chi tiết. */
    public void replaceFlag(ExamQuestionFlag flag, String detail) {
        this.flag = flag;
        addNote(detail);
    }

    public void clearFlag() {
        this.flag = null;
    }

    /** Nối thêm vào ghi chú đang có. */
    public void addNote(String detail) {
        this.reviewNote = reviewNote == null ? detail : reviewNote + " " + detail;
    }

    /** Ghi chú của người duyệt thay cho ghi chú cũ. */
    public void replaceNote(String note) {
        this.reviewNote = note;
    }
}
