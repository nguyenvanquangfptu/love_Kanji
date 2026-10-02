package com.kanjimastery.backend.controller;

import com.kanjimastery.backend.dto.ExamSittingResponse;
import com.kanjimastery.backend.dto.JlptLevelResponse;
import com.kanjimastery.backend.dto.StartExamResponse;
import com.kanjimastery.backend.dto.StartJlptExamRequest;
import com.kanjimastery.backend.service.JlptExamService;
import com.kanjimastery.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/exams/jlpt")
@RequiredArgsConstructor
@Tag(name = "JLPT Exam", description = "Làm đề JLPT theo cấu trúc đề thật: phần Từ vựng và Ngữ pháp, mỗi phần có giờ riêng")
public class JlptExamController {

    private final JlptExamService jlptExamService;
    private final UserService userService;

    @Operation(summary = "Cấu trúc đề theo cấp độ",
            description = "Các phần, các 問題 với số câu của đề thật và số câu đã duyệt hiện có; đề ghép được lúc này "
                    + "có bao nhiêu câu, bao nhiêu phút.")
    @GetMapping("/levels")
    public ResponseEntity<List<JlptLevelResponse>> levels() {
        return ResponseEntity.ok(jlptExamService.levels());
    }

    @Operation(summary = "Bắt đầu làm đề",
            description = "Tạo buổi thi với các phần đã chọn và bắt đầu ngay phần đầu tiên - một lượt thi có giờ "
                    + "riêng, dùng chung API lưu đáp án/nộp bài/xem lại với thi nhanh. Dạng câu chưa đủ câu thì đề "
                    + "ngắn hơn đề thật, thời gian giảm theo tỉ lệ.")
    @PostMapping("/sittings")
    public ResponseEntity<StartExamResponse> start(Authentication authentication,
                                                   @Valid @RequestBody StartJlptExamRequest request) {
        return ResponseEntity.ok(jlptExamService.startSitting(currentUserId(authentication), request));
    }

    @Operation(summary = "Bắt đầu phần tiếp theo",
            description = "Sau khi phần trước đã nộp hoặc hết giờ. Mỗi từ chỉ được hỏi một lần trong cả buổi thi.")
    @PostMapping("/sittings/{sittingId}/next")
    public ResponseEntity<StartExamResponse> next(Authentication authentication, @PathVariable Long sittingId) {
        return ResponseEntity.ok(jlptExamService.startNextSection(currentUserId(authentication), sittingId));
    }

    @GetMapping("/sittings/{sittingId}")
    public ResponseEntity<ExamSittingResponse> get(Authentication authentication, @PathVariable Long sittingId) {
        return ResponseEntity.ok(jlptExamService.getSitting(currentUserId(authentication), sittingId));
    }

    private Long currentUserId(Authentication authentication) {
        return userService.getByUsername(authentication.getName()).getId();
    }
}
