package com.kanjimastery.backend.controller;

import com.kanjimastery.backend.dto.QuestionReportRequest;
import com.kanjimastery.backend.service.QuestionReportService;
import com.kanjimastery.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/exams/questions")
@RequiredArgsConstructor
@Tag(name = "Question reports", description = "Người học báo lỗi câu hỏi thi")
public class QuestionReportController {

    private final QuestionReportService reportService;
    private final UserService userService;

    @Operation(summary = "Báo lỗi một câu hỏi",
            description = "Chỉ câu đã làm trong bài thi đã nộp. Lý do: WRONG_ANSWER, AMBIGUOUS, UNCLEAR, OTHER. Báo lại "
                    + "thì cập nhật lý do. Câu bị " + QuestionReportService.AUTO_WITHDRAW_REPORTS + " người báo thì tự "
                    + "rút khỏi đề chờ duyệt lại.")
    @PostMapping("/{questionId}/reports")
    public ResponseEntity<Void> report(Authentication authentication, @PathVariable Long questionId,
                                       @Valid @RequestBody QuestionReportRequest request) {
        Long userId = userService.getByUsername(authentication.getName()).getId();
        reportService.report(userId, questionId, request.getReason(), request.getNote());
        return ResponseEntity.noContent().build();
    }
}
