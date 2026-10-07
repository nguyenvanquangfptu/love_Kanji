package com.kanjimastery.backend.controller;

import com.kanjimastery.backend.model.ExamQuestionStatus;
import com.kanjimastery.backend.dto.AdminExamPassageResponse;
import com.kanjimastery.backend.dto.ExamPassageRequest;
import com.kanjimastery.backend.dto.ExamQuestionStatusRequest;
import com.kanjimastery.backend.dto.PassageDraftRequest;
import com.kanjimastery.backend.service.ExamPassageReviewService;
import com.kanjimastery.backend.service.PassageDraftService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/exam-passages")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Exam passages", description = "Duyệt đoạn văn 文章の文法 (chỉ ADMIN)")
public class AdminExamPassageController {

    private final ExamPassageReviewService passageReviewService;
    private final PassageDraftService passageDraftService;

    @Operation(summary = "Đoạn văn của một cấp độ", description = "Mới nhất trước, kèm câu hỏi theo thứ tự chỗ trống.")
    @GetMapping
    public ResponseEntity<Page<AdminExamPassageResponse>> search(@RequestParam String level,
                                                                 @RequestParam(required = false) ExamQuestionStatus status,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(passageReviewService.search(level, status, page, Math.min(Math.max(size, 1), 50)));
    }

    @Operation(summary = "Sửa tiêu đề, nội dung đoạn văn", description = "Chỗ trống 【n】 phải khớp với câu hỏi của đoạn.")
    @PutMapping("/{id}")
    public ResponseEntity<AdminExamPassageResponse> update(@PathVariable Long id,
                                                           @Valid @RequestBody ExamPassageRequest request) {
        return ResponseEntity.ok(passageReviewService.update(id, request.getTitle(), request.getContent()));
    }

    @Operation(summary = "Duyệt / loại / rút cả đoạn văn",
            description = "Áp dụng cho cả đoạn và mọi câu hỏi của nó. REJECTED cần lý do; APPROVED cần đoạn và câu hợp lệ.")
    @PostMapping("/{id}/status")
    public ResponseEntity<AdminExamPassageResponse> changeStatus(@PathVariable Long id,
                                                                 @Valid @RequestBody ExamQuestionStatusRequest request) {
        return ResponseEntity.ok(passageReviewService.changeStatus(id, request.getStatus(), request.getNote()));
    }

    @Operation(summary = "Nhờ AI viết nháp một đoạn văn",
            description = "Đoạn văn mới có số chỗ trống theo cấu trúc đề của cấp độ. Kiểm tra tự động (chỗ trống khớp "
                    + "câu hỏi, từ vượt cấp, AI giải lại) rồi vào hàng chờ duyệt; sai cấu trúc thì bị loại cả đoạn. "
                    + "Tốn 2 request Gemini. Chưa cấu hình GEMINI_API_KEY hoặc Gemini không trả lời thì trả 400.")
    @PostMapping("/drafts")
    public ResponseEntity<PassageDraftService.PassageDraftResult> draft(@Valid @RequestBody PassageDraftRequest request) {
        return ResponseEntity.ok(passageDraftService.draft(request.getLevel()));
    }
}
