package com.kanjimastery.backend.controller;

import com.kanjimastery.backend.dto.ExamResultResponse;
import com.kanjimastery.backend.dto.ExamReviewResponse;
import com.kanjimastery.backend.dto.ExamSessionResponse;
import com.kanjimastery.backend.dto.SaveAnswerRequest;
import com.kanjimastery.backend.dto.StartExamRequest;
import com.kanjimastery.backend.dto.StartExamResponse;
import com.kanjimastery.backend.service.ExamService;
import com.kanjimastery.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/exams")
@RequiredArgsConstructor
@Tag(name = "Exam", description = "Thi thử JLPT: bắt đầu, tự động lưu đáp án, nộp bài, xem lại")
public class ExamController {

    private final ExamService examService;
    private final UserService userService;

    @Operation(summary = "Bắt đầu bài thi",
            description = "Lấy N câu hỏi ngẫu nhiên theo cấp độ, tạo attempt IN_PROGRESS. remainingSeconds dùng để Frontend đếm ngược cục bộ, tránh lệch giờ do đồng hồ client sai (clock drift).")
    @PostMapping("/start")
    public ResponseEntity<StartExamResponse> start(Authentication authentication,
                                                     @Valid @RequestBody StartExamRequest request) {
        return ResponseEntity.ok(examService.start(currentUserId(authentication), request));
    }

    @PutMapping("/attempts/{attemptId}/answers")
    public ResponseEntity<Void> saveAnswer(Authentication authentication,
                                            @PathVariable Long attemptId,
                                            @Valid @RequestBody SaveAnswerRequest request) {
        examService.saveAnswer(currentUserId(authentication), attemptId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/attempts/{attemptId}/session")
    public ResponseEntity<ExamSessionResponse> getSession(Authentication authentication, @PathVariable Long attemptId) {
        return ResponseEntity.ok(examService.getSession(currentUserId(authentication), attemptId));
    }

    @Operation(summary = "Nộp bài thủ công",
            description = "Gọi vào ExamFinalizationService.finalize() dùng chung với Keyspace Event/Reconciliation Job - CAS UPDATE đảm bảo chỉ 1 trong 3 luồng được phép ghi kết quả.")
    @PostMapping("/attempts/{attemptId}/submit")
    public ResponseEntity<ExamResultResponse> submit(Authentication authentication, @PathVariable Long attemptId) {
        return ResponseEntity.ok(examService.submit(currentUserId(authentication), attemptId));
    }

    @GetMapping("/attempts/{attemptId}/review")
    public ResponseEntity<ExamReviewResponse> getReview(Authentication authentication, @PathVariable Long attemptId) {
        return ResponseEntity.ok(examService.getReview(currentUserId(authentication), attemptId));
    }

    private Long currentUserId(Authentication authentication) {
        return userService.getByUsername(authentication.getName()).getId();
    }
}
