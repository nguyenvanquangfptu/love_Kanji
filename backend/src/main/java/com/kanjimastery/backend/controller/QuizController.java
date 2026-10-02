package com.kanjimastery.backend.controller;

import com.kanjimastery.backend.dto.QuizAnswerRequest;
import com.kanjimastery.backend.dto.QuizAnswerResponse;
import com.kanjimastery.backend.dto.QuizQuestionResponse;
import com.kanjimastery.backend.service.QuizAnswerService;
import com.kanjimastery.backend.service.QuizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/quiz")
@RequiredArgsConstructor
@Tag(name = "Quiz", description = "Trắc nghiệm ôn tập từ vựng theo tag/cấp độ; kết quả từng câu được ghi lại và cập nhật lịch ôn")
public class QuizController {

    private final QuizService quizService;
    private final QuizAnswerService quizAnswerService;

    @Operation(summary = "Sinh bộ câu hỏi trắc nghiệm từ danh sách từ vựng lọc theo tag và/hoặc cấp độ JLPT",
            description = "mode=adaptive (mặc định): khoảng 60% từ đang yếu, 25% từ chưa gặp, 15% từ đã thuộc, hướng hỏi "
                    + "nghiêng về chiều người học hay sai. mode=random: chọn từ và hướng hỏi ngẫu nhiên đều. "
                    + "hardWords=true: chỉ lấy từ khó của người học (bỏ qua tagId/level).")
    @GetMapping("/generate")
    public ResponseEntity<List<QuizQuestionResponse>> generate(
            Authentication authentication,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) String level,
            @RequestParam(required = false, defaultValue = "10") Integer size,
            @RequestParam(required = false, defaultValue = QuizService.MODE_ADAPTIVE) String mode,
            @RequestParam(required = false, defaultValue = "false") boolean hardWords) {
        return ResponseEntity.ok(quizService.generate(authentication.getName(), tagId, level, size, mode, hardWords));
    }

    @Operation(summary = "Gửi kết quả một câu trắc nghiệm",
            description = "Server tự chấm theo kanjiId + direction rồi cập nhật lịch ôn: làm sai thì từ được đưa vào Ôn tập "
                    + "(đã có thì học lại từ đầu); làm đúng khi thẻ đã đến hạn thì tính là một lần ôn, mức độ nhớ suy từ "
                    + "thời gian trả lời so với chính người học; làm đúng khi chưa đến hạn thì chỉ ghi lại.")
    @PostMapping("/answers")
    public ResponseEntity<QuizAnswerResponse> submitAnswer(
            Authentication authentication,
            @Valid @RequestBody QuizAnswerRequest request) {
        return ResponseEntity.ok(quizAnswerService.submit(authentication.getName(), request));
    }
}
