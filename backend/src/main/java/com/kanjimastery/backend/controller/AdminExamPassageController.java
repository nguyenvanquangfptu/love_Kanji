package com.kanjimastery.backend.controller;

import com.kanjimastery.backend.dto.AdminExamPassageResponse;
import com.kanjimastery.backend.dto.ExamPassageRequest;
import com.kanjimastery.backend.dto.ExamQuestionStatusRequest;
import com.kanjimastery.backend.service.ExamPassageReviewService;
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

    @Operation(summary = "Đoạn văn của một cấp độ", description = "Mới nhất trước, kèm câu hỏi theo thứ tự chỗ trống.")
    @GetMapping
    public ResponseEntity<Page<AdminExamPassageResponse>> search(@RequestParam String level,
                                                                 @RequestParam(required = false) String status,
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
}
