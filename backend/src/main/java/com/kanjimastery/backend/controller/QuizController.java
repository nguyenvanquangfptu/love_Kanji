package com.kanjimastery.backend.controller;

import com.kanjimastery.backend.dto.QuizQuestionResponse;
import com.kanjimastery.backend.service.QuizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/quiz")
@RequiredArgsConstructor
@Tag(name = "Quiz", description = "Sinh câu hỏi trắc nghiệm ôn tập từ vựng theo tag/cấp độ (không lưu SRS)")
public class QuizController {

    private final QuizService quizService;

    @Operation(summary = "Sinh bộ câu hỏi trắc nghiệm từ danh sách từ vựng lọc theo tag và/hoặc cấp độ JLPT")
    @GetMapping("/generate")
    public ResponseEntity<List<QuizQuestionResponse>> generate(
            Authentication authentication,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) String level,
            @RequestParam(required = false, defaultValue = "10") Integer size) {
        return ResponseEntity.ok(quizService.generate(authentication.getName(), tagId, level, size));
    }
}
