package com.kanjimastery.backend.controller;

import com.kanjimastery.backend.model.ExamQuestionStatus;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.dto.AdminExamQuestionRequest;
import com.kanjimastery.backend.dto.AdminExamQuestionResponse;
import com.kanjimastery.backend.dto.BulkApproveRequest;
import com.kanjimastery.backend.dto.ExamQuestionStatusRequest;
import com.kanjimastery.backend.dto.QuestionBankStatsResponse;
import com.kanjimastery.backend.dto.QuestionDraftRequest;
import com.kanjimastery.backend.dto.VocabularyDraftRequest;
import com.kanjimastery.backend.service.ExamQuestionReviewService;
import com.kanjimastery.backend.service.ItemAnalysisService;
import com.kanjimastery.backend.service.QuestionDraftService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/exam-questions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Exam questions", description = "Duyệt câu thi đề JLPT (chỉ ADMIN)")
public class AdminExamQuestionController {

    private final ExamQuestionReviewService reviewService;
    private final QuestionDraftService draftService;
    private final ItemAnalysisService itemAnalysisService;

    @Operation(summary = "Lọc câu thi đề JLPT", description = "Mới nhất trước. Chỉ câu thuộc một dạng đề JLPT.")
    @GetMapping
    public ResponseEntity<Page<AdminExamQuestionResponse>> search(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) JlptQuestionType type,
            @RequestParam(required = false) ExamQuestionStatus status,
            @RequestParam(defaultValue = "false") boolean flagged,
            @RequestParam(required = false) Long grammarPointId,
            @RequestParam(defaultValue = "false") boolean reported,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ExamQuestionReviewService.Filter filter =
                new ExamQuestionReviewService.Filter(level, type, status, flagged, grammarPointId, reported);
        return ResponseEntity.ok(reviewService.search(filter, page, Math.min(Math.max(size, 1), 100)));
    }

    @Operation(summary = "Sửa nội dung câu thi", description = "Câu phải đúng cấu trúc của dạng câu; không đổi trạng thái.")
    @PutMapping("/{id}")
    public ResponseEntity<AdminExamQuestionResponse> update(@PathVariable Long id,
                                                            @Valid @RequestBody AdminExamQuestionRequest request) {
        return ResponseEntity.ok(reviewService.update(id, request));
    }

    @Operation(summary = "Duyệt / loại / rút câu thi",
            description = "APPROVED: vào đề được (câu sai cấu trúc thì không duyệt được); REJECTED: cần ghi lý do; "
                    + "RETIRED: rút khỏi đề; DRAFT: đưa về chờ duyệt.")
    @PostMapping("/{id}/status")
    public ResponseEntity<AdminExamQuestionResponse> changeStatus(@PathVariable Long id,
                                                                  @Valid @RequestBody ExamQuestionStatusRequest request) {
        return ResponseEntity.ok(reviewService.changeStatus(id, request.getStatus(), request.getNote()));
    }

    @Operation(summary = "Duyệt nhiều câu một lượt",
            description = "Tối đa 100 câu. Chỉ duyệt câu đang chờ duyệt, không có cảnh báo, không thuộc đoạn văn và đúng "
                    + "cấu trúc; câu khác giữ nguyên và được trả về kèm lý do.")
    @PostMapping("/approve")
    public ResponseEntity<ExamQuestionReviewService.BulkApproval> approveAll(
            @Valid @RequestBody BulkApproveRequest request) {
        return ResponseEntity.ok(reviewService.approveAll(request.getIds()));
    }

    @Operation(summary = "Bỏ qua báo lỗi của một câu",
            description = "Người duyệt thấy câu không sai: đóng các báo lỗi đang mở, bỏ cờ báo lỗi. Không đổi trạng thái "
                    + "câu - câu đã tự rút khỏi đề thì duyệt lại để đưa vào đề.")
    @PostMapping("/{id}/reports/dismiss")
    public ResponseEntity<AdminExamQuestionResponse> dismissReports(@PathVariable Long id) {
        return ResponseEntity.ok(reviewService.dismissReports(id));
    }

    @Operation(summary = "Phân tích câu hỏi ngay",
            description = "Như job hằng tuần: tính thống kê từng câu từ kết quả thi (từ lần duyệt gần nhất), gắn cờ "
                    + "câu đã duyệt có từ " + ItemAnalysisService.MIN_FLAG_RESPONSES + " lượt làm mà người làm tốt lại "
                    + "hay sai.")
    @PostMapping("/analysis")
    public ResponseEntity<ItemAnalysisService.Summary> analyze() {
        return ResponseEntity.ok(itemAnalysisService.analyze());
    }

    @Operation(summary = "Ngân hàng câu theo cấp độ và dạng câu", description = "Số câu theo trạng thái, đủ cho bao nhiêu đề.")
    @GetMapping("/stats")
    public ResponseEntity<List<QuestionBankStatsResponse>> stats() {
        return ResponseEntity.ok(reviewService.stats());
    }

    @Operation(summary = "Nhờ AI viết nháp câu ngữ pháp",
            description = "Cho một điểm ngữ pháp, dạng GRAMMAR_FORM hoặc SENTENCE_ORDER, tối đa 8 câu. Câu được kiểm tra "
                    + "tự động (cấu trúc, từ vượt cấp, AI giải lại) rồi vào hàng chờ duyệt; sai cấu trúc thì bị loại. "
                    + "Tốn 2 request Gemini. Chưa cấu hình GEMINI_API_KEY hoặc Gemini không trả lời thì trả 400.")
    @PostMapping("/drafts")
    public ResponseEntity<QuestionDraftService.DraftResult> draft(@Valid @RequestBody QuestionDraftRequest request) {
        return ResponseEntity.ok(draftService.draft(request.getGrammarPointId(), request.getType(), request.getCount()));
    }

    @Operation(summary = "Nhờ AI viết nháp câu từ vựng",
            description = "Dạng PARAPHRASE hoặc USAGE (phải có trong đề của cấp độ), tối đa 8 câu, mỗi câu cho một từ "
                    + "trong bài của cấp độ chưa có câu dạng đó. Kiểm tra tự động như câu ngữ pháp; tốn 2 request "
                    + "Gemini. Chưa cấu hình GEMINI_API_KEY, Gemini không trả lời hoặc hết từ thì trả 400.")
    @PostMapping("/vocabulary-drafts")
    public ResponseEntity<QuestionDraftService.DraftResult> draftVocabulary(
            @Valid @RequestBody VocabularyDraftRequest request) {
        return ResponseEntity.ok(draftService.draftVocabulary(request.getLevel(), request.getType(),
                request.getCount()));
    }
}
