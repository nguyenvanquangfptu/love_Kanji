package com.kanjimastery.backend.controller;

import com.kanjimastery.backend.dto.AddSrsCardsRequest;
import com.kanjimastery.backend.dto.AddSrsCardsResponse;
import com.kanjimastery.backend.dto.DailyCardResponse;
import com.kanjimastery.backend.dto.ReviewRequest;
import com.kanjimastery.backend.dto.ReviewResponse;
import com.kanjimastery.backend.dto.SrsStatsResponse;
import com.kanjimastery.backend.dto.SrsTagStatusResponse;
import com.kanjimastery.backend.model.User;
import com.kanjimastery.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.service.SrsService;

@RestController
@RequestMapping("/api/v1/srs")
@RequiredArgsConstructor
@Tag(name = "SRS (Spaced Repetition)", description = "Ôn tập Kanji theo thuật toán SuperMemo SM-2")
public class SrsController {

    private final SrsService srsService;
    private final UserService userService;

    @GetMapping("/daily-cards")
    public ResponseEntity<Page<DailyCardResponse>> getDailyCards(
            Authentication authentication,
            @PageableDefault(size = 20) Pageable pageable) {
        Long userId = currentUserId(authentication);
        return ResponseEntity.ok(srsService.getDailyCards(userId, pageable));
    }

    @Operation(summary = "Chấm điểm ôn tập (SM-2)",
            description = "Nhận đánh giá quality (0-5), tính lại easinessFactor/interval theo SuperMemo SM-2 và cập nhật lịch ôn tiếp theo. Tự tạo bản ghi SRS nếu đây là lần ôn đầu tiên của Kanji này.")
    @PostMapping("/review")
    public ResponseEntity<ReviewResponse> submitReview(
            Authentication authentication,
            @Valid @RequestBody ReviewRequest request) {
        Long userId = currentUserId(authentication);
        return ResponseEntity.ok(srsService.submitReview(userId, request));
    }

    @Operation(summary = "Thêm từ vào lịch ôn tập",
            description = "Đưa danh sách từ (cả một bài, hoặc các từ làm sai trong trắc nghiệm) vào lịch ôn SRS, đến hạn ôn ngay. Từ đã có trong lịch ôn giữ nguyên tiến độ, nên gọi lại nhiều lần vẫn an toàn.")
    @PostMapping("/cards")
    public ResponseEntity<AddSrsCardsResponse> addCards(
            Authentication authentication,
            @Valid @RequestBody AddSrsCardsRequest request) {
        Long userId = currentUserId(authentication);
        return ResponseEntity.ok(srsService.addCards(userId, request.getKanjiIds()));
    }

    @Operation(summary = "Số từ của một bài (tag) đã có trong lịch ôn tập")
    @GetMapping("/cards/status")
    public ResponseEntity<SrsTagStatusResponse> getTagStatus(
            Authentication authentication,
            @RequestParam Long tagId) {
        Long userId = currentUserId(authentication);
        return ResponseEntity.ok(srsService.getTagStatus(userId, tagId));
    }

    @GetMapping("/stats")
    public ResponseEntity<SrsStatsResponse> getStats(Authentication authentication) {
        Long userId = currentUserId(authentication);
        return ResponseEntity.ok(srsService.getStats(userId));
    }

    private Long currentUserId(Authentication authentication) {
        User user = userService.getByUsername(authentication.getName());
        return user.getId();
    }
}
