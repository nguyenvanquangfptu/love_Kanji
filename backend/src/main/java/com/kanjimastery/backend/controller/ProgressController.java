package com.kanjimastery.backend.controller;

import com.kanjimastery.backend.dto.ProgressResponse;
import com.kanjimastery.backend.service.ProgressService;
import com.kanjimastery.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/progress")
@RequiredArgsConstructor
@Tag(name = "Progress", description = "Tiến bộ của người học, tính từ lịch sử trả lời")
public class ProgressController {

    private final ProgressService progressService;
    private final UserService userService;

    @Operation(summary = "Tiến bộ của tôi",
            description = "Tỉ lệ nhớ thật theo tuần (8 tuần), lượt ôn và từ mới theo ngày (14 ngày), độ chính xác "
                    + "trắc nghiệm theo hướng hỏi (30 ngày) và những đáp án sai hay chọn nhất (90 ngày).")
    @GetMapping
    public ResponseEntity<ProgressResponse> getProgress(Authentication authentication) {
        return ResponseEntity.ok(progressService.get(userService.getByUsername(authentication.getName()).getId()));
    }
}
