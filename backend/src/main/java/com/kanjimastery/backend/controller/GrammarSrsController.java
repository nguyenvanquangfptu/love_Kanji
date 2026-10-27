package com.kanjimastery.backend.controller;

import com.kanjimastery.backend.dto.AddGrammarCardsRequest;
import com.kanjimastery.backend.dto.AddSrsCardsResponse;
import com.kanjimastery.backend.dto.GrammarPointStatusResponse;
import com.kanjimastery.backend.dto.GrammarReviewRequest;
import com.kanjimastery.backend.dto.GrammarReviewResponse;
import com.kanjimastery.backend.dto.GrammarSessionResponse;
import com.kanjimastery.backend.service.GrammarSrsService;
import com.kanjimastery.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/grammar-srs")
@RequiredArgsConstructor
@Tag(name = "Ôn ngữ pháp", description = "Ôn các điểm ngữ pháp theo lịch SM-2/FSRS như thẻ từ vựng")
public class GrammarSrsController {

    private final GrammarSrsService grammarSrsService;
    private final UserService userService;

    @Operation(summary = "Điểm ngữ pháp của một cấp độ",
            description = "Theo bài trong giáo trình (N3 không theo bài), kèm điểm nào người học đã đưa vào lịch ôn.")
    @GetMapping("/points")
    public ResponseEntity<List<GrammarPointStatusResponse>> points(Authentication authentication,
                                                                   @RequestParam String level) {
        return ResponseEntity.ok(grammarSrsService.points(currentUserId(authentication), level));
    }

    @Operation(summary = "Thêm điểm ngữ pháp vào lịch ôn",
            description = "Điểm đã có giữ nguyên tiến độ, nên gọi lại nhiều lần vẫn an toàn.")
    @PostMapping("/cards")
    public ResponseEntity<AddSrsCardsResponse> addCards(Authentication authentication,
                                                        @Valid @RequestBody AddGrammarCardsRequest request) {
        return ResponseEntity.ok(grammarSrsService.addCards(currentUserId(authentication),
                request.getGrammarPointIds()));
    }

    @Operation(summary = "Phiên ôn ngữ pháp hôm nay",
            description = "Điểm đến hạn trước, rồi điểm mới trong giới hạn app.srs.grammar-new-per-day mỗi ngày. Điểm có câu "
                    + "đã duyệt kèm một câu hỏi (không kèm đáp án), điểm chưa có câu thì ôn bằng thẻ lật.")
    @GetMapping("/session")
    public ResponseEntity<GrammarSessionResponse> session(Authentication authentication) {
        return ResponseEntity.ok(grammarSrsService.session(currentUserId(authentication)));
    }

    @Operation(summary = "Chấm một lần ôn ngữ pháp",
            description = "Câu hỏi: gửi questionId + chosenOption, server chấm (đúng = Nhớ, sai = Quên). Thẻ lật: gửi rating 1-4.")
    @PostMapping("/reviews")
    public ResponseEntity<GrammarReviewResponse> review(Authentication authentication,
                                                        @Valid @RequestBody GrammarReviewRequest request) {
        return ResponseEntity.ok(grammarSrsService.review(currentUserId(authentication), request));
    }

    private Long currentUserId(Authentication authentication) {
        return userService.getByUsername(authentication.getName()).getId();
    }
}
