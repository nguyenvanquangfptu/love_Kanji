package com.kanjimastery.backend.controller;

import com.kanjimastery.backend.dto.ExamSittingResponse;
import com.kanjimastery.backend.dto.JlptLeaderboardEntryResponse;
import com.kanjimastery.backend.dto.JlptLevelResponse;
import com.kanjimastery.backend.dto.PracticeQuestionResponse;
import com.kanjimastery.backend.dto.StartExamResponse;
import com.kanjimastery.backend.dto.StartJlptExamRequest;
import com.kanjimastery.backend.dto.WeakGrammarResponse;
import com.kanjimastery.backend.service.GrammarPracticeService;
import com.kanjimastery.backend.service.JlptExamService;
import com.kanjimastery.backend.service.LeaderboardService;
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
    private final GrammarPracticeService grammarPracticeService;
    private final LeaderboardService leaderboardService;
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

    @Operation(summary = "Điểm ngữ pháp hay làm sai",
            description = "Trong các đề JLPT của cấp độ " + GrammarPracticeService.MISTAKE_DAYS + " ngày qua: các điểm "
                    + "ngữ pháp có câu làm sai (không tính câu bỏ trống), sai nhiều nhất trước, tối đa "
                    + GrammarPracticeService.MAX_WEAK_POINTS + " điểm.")
    @GetMapping("/weak-grammar")
    public ResponseEntity<List<WeakGrammarResponse>> weakGrammar(Authentication authentication,
                                                                 @RequestParam String level) {
        return ResponseEntity.ok(grammarPracticeService.weakPoints(currentUserId(authentication), level));
    }

    @Operation(summary = "Câu luyện lại theo điểm ngữ pháp",
            description = "Tối đa " + GrammarPracticeService.MAX_PRACTICE_QUESTIONS + " câu đã duyệt của cấp độ (không "
                    + "thuộc đoạn văn) gắn với các điểm đã chọn, thứ tự ngẫu nhiên, kèm đáp án và giải thích. Luyện "
                    + "không tính giờ, không lưu kết quả.")
    @GetMapping("/grammar-practice")
    public ResponseEntity<List<PracticeQuestionResponse>> grammarPractice(@RequestParam String level,
                                                                          @RequestParam List<Long> grammarPointIds) {
        return ResponseEntity.ok(grammarPracticeService.practice(level, grammarPointIds));
    }

    @Operation(summary = "Bảng xếp hạng đề JLPT của cấp độ",
            description = "Mỗi người học một dòng: buổi thi làm đủ các phần có tỉ lệ đúng cao nhất, bằng nhau thì ai "
                    + "làm nhanh hơn xếp trên. Tách riêng với bảng xếp hạng thi nhanh.")
    @GetMapping("/leaderboard")
    public ResponseEntity<List<JlptLeaderboardEntryResponse>> leaderboard(@RequestParam String level,
                                                                          @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(leaderboardService.getJlptTop(level.toUpperCase(), Math.min(Math.max(limit, 1), 50)));
    }

    @Operation(summary = "Hạng của tôi trên bảng xếp hạng đề JLPT",
            description = "Hạng null khi chưa có buổi thi trọn vẹn nào ở cấp độ này.")
    @GetMapping("/leaderboard/me")
    public ResponseEntity<JlptLeaderboardEntryResponse> myRank(Authentication authentication,
                                                               @RequestParam String level) {
        return ResponseEntity.ok(leaderboardService.getMyJlptRank(level.toUpperCase(), currentUserId(authentication)));
    }

    private Long currentUserId(Authentication authentication) {
        return userService.getByUsername(authentication.getName()).getId();
    }
}
