package com.kanjimastery.backend.controller;

import com.kanjimastery.backend.dto.FsrsParametersResponse;
import com.kanjimastery.backend.dto.LearningProfileRequest;
import com.kanjimastery.backend.dto.LearningProfileResponse;
import com.kanjimastery.backend.service.FsrsParametersService;
import com.kanjimastery.backend.service.LearningProfileService;
import com.kanjimastery.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@Tag(name = "Profile", description = "Mục tiêu học của người học")
public class ProfileController {

    private final LearningProfileService learningProfileService;
    private final FsrsParametersService fsrsParametersService;
    private final UserService userService;

    @Operation(summary = "Mục tiêu học hiện tại", description = "Chưa đặt thì configured=false và các giá trị mặc định.")
    @GetMapping("/learning")
    public ResponseEntity<LearningProfileResponse> getLearningProfile(Authentication authentication) {
        return ResponseEntity.ok(learningProfileService.get(currentUserId(authentication)));
    }

    @Operation(summary = "Đặt mục tiêu học",
            description = "Cấp độ JLPT nhắm tới và ngày thi để app tính số từ mới mỗi ngày, số phút ôn mỗi ngày, "
                    + "hoặc tự đặt số từ mới mỗi ngày (newWordsPerDay).")
    @PutMapping("/learning")
    public ResponseEntity<LearningProfileResponse> updateLearningProfile(
            Authentication authentication,
            @Valid @RequestBody LearningProfileRequest request) {
        return ResponseEntity.ok(learningProfileService.update(currentUserId(authentication), request));
    }

    @Operation(summary = "Tối ưu tham số FSRS theo lịch sử ôn",
            description = "Tìm độ ổn định sau lần học đầu khớp với trí nhớ của chính người học (cũng tự chạy mỗi tuần). "
                    + "Mức chấm nào chưa đủ dữ liệu thì suy từ các mức đã đủ; chưa mức nào đủ thì giữ tham số đang dùng.")
    @PostMapping("/learning/fsrs/optimize")
    public ResponseEntity<FsrsParametersResponse> optimizeFsrs(Authentication authentication) {
        return ResponseEntity.ok(fsrsParametersService.optimize(currentUserId(authentication)));
    }

    private Long currentUserId(Authentication authentication) {
        return userService.getByUsername(authentication.getName()).getId();
    }
}
