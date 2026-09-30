package com.kanjimastery.backend.controller;

import com.kanjimastery.backend.dto.LeaderboardEntryResponse;
import com.kanjimastery.backend.dto.MyRankResponse;
import com.kanjimastery.backend.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.kanjimastery.backend.service.LeaderboardService;

@RestController
@RequestMapping("/api/v1/exams")
@RequiredArgsConstructor
@Tag(name = "Leaderboard", description = "Bảng xếp hạng real-time bằng Redis Sorted Set")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;
    private final UserService userService;

    @GetMapping("/leaderboard")
    public ResponseEntity<List<LeaderboardEntryResponse>> getLeaderboard(
            @RequestParam String level,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(leaderboardService.getTop(level.toUpperCase(), limit));
    }

    @GetMapping("/my-rank")
    public ResponseEntity<MyRankResponse> getMyRank(Authentication authentication, @RequestParam String level) {
        Long userId = userService.getByUsername(authentication.getName()).getId();
        return ResponseEntity.ok(leaderboardService.getMyRank(level.toUpperCase(), userId));
    }
}
