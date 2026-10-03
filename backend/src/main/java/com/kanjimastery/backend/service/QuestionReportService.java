package com.kanjimastery.backend.service;

import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.exception.ResourceNotFoundException;
import com.kanjimastery.backend.model.ExamPassage;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionFlag;
import com.kanjimastery.backend.model.ExamQuestionReport;
import com.kanjimastery.backend.model.ExamQuestionStatus;
import com.kanjimastery.backend.model.QuestionReportReason;
import com.kanjimastery.backend.model.QuestionReportStatus;
import com.kanjimastery.backend.repository.ExamPassageRepository;
import com.kanjimastery.backend.repository.ExamQuestionReportRepository;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.UserExamAnswerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Người học báo lỗi câu hỏi từ trang xem lại bài thi. Chỉ báo được câu mình đã làm trong bài đã nộp; mỗi người một báo
 * cáo cho mỗi câu, báo lại thì cập nhật lý do. Câu đã duyệt bị {@value #AUTO_WITHDRAW_REPORTS} người báo (báo cáo đang
 * mở) thì tự rút về chờ duyệt, gắn cờ cho người duyệt - câu của đoạn văn thì rút cả đoạn, vì đề lấy trọn đoạn.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionReportService {

    public static final int AUTO_WITHDRAW_REPORTS = 3;

    private final ExamQuestionReportRepository reportRepository;
    private final ExamQuestionRepository questionRepository;
    private final ExamPassageRepository passageRepository;
    private final UserExamAnswerRepository answerRepository;

    @Transactional
    public void report(Long userId, Long questionId, String reason, String note) {
        if (!QuestionReportReason.ALL.contains(reason)) {
            throw new BadRequestException("Lý do báo lỗi không hợp lệ: " + reason);
        }
        ExamQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy câu hỏi: " + questionId));
        if (!answerRepository.existsInFinishedAttemptOf(userId, questionId)) {
            throw new BadRequestException("Chỉ báo lỗi được câu bạn đã làm trong một bài thi đã nộp");
        }
        ExamQuestionReport report = reportRepository.findByQuestionIdAndUserId(questionId, userId)
                .orElseGet(() -> ExamQuestionReport.builder().questionId(questionId).userId(userId).build());
        report.setReason(reason);
        report.setNote(StringUtils.hasText(note) ? note.strip() : null);
        report.setStatus(QuestionReportStatus.OPEN);
        report.setCreatedAt(LocalDateTime.now());
        report.setResolvedAt(null);
        reportRepository.save(report);

        long open = reportRepository.countByQuestionIdAndStatus(questionId, QuestionReportStatus.OPEN);
        if (open >= AUTO_WITHDRAW_REPORTS && ExamQuestionStatus.APPROVED.equals(question.getStatus())) {
            withdraw(question, open);
        }
    }

    /** Rút câu (hoặc cả đoạn văn của câu) về chờ duyệt, gắn cờ báo lỗi. */
    private void withdraw(ExamQuestion question, long reports) {
        String note = "Rút khỏi đề tự động: " + reports + " người học báo lỗi - xem các báo lỗi rồi sửa, duyệt lại "
                + "hoặc loại.";
        flag(question, note);
        if (question.getPassageId() == null) {
            question.setStatus(ExamQuestionStatus.DRAFT);
            log.info("Câu {} bị {} người báo lỗi - rút về chờ duyệt.", question.getId(), reports);
            return;
        }
        ExamPassage passage = passageRepository.findById(question.getPassageId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đoạn văn: " + question.getPassageId()));
        passage.setStatus(ExamQuestionStatus.DRAFT);
        passage.setFlag(ExamQuestionFlag.REPORTED);
        passage.setReviewNote(passage.getReviewNote() == null ? note : passage.getReviewNote() + " " + note);
        List<ExamQuestion> blanks = questionRepository.findAllWithLinksByPassageIdIn(List.of(passage.getId()));
        blanks.forEach(blank -> blank.setStatus(ExamQuestionStatus.DRAFT));
        question.setStatus(ExamQuestionStatus.DRAFT);
        log.info("Câu {} (đoạn văn {}) bị {} người báo lỗi - rút cả đoạn về chờ duyệt.", question.getId(),
                passage.getId(), reports);
    }

    private static void flag(ExamQuestion question, String note) {
        question.setFlag(ExamQuestionFlag.REPORTED);
        question.setReviewNote(question.getReviewNote() == null ? note : question.getReviewNote() + " " + note);
    }
}
