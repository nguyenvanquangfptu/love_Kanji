package com.kanjimastery.backend.service;

import com.kanjimastery.backend.dto.LeaderboardEntryResponse;
import com.kanjimastery.backend.dto.MyRankResponse;
import com.kanjimastery.backend.model.User;
import com.kanjimastery.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bảng xếp hạng Real-time bằng Redis Sorted Set - loại bỏ hoàn toàn các truy
 * vấn sắp xếp nặng vào PostgreSQL. Score kết hợp điểm số và tốc độ hoàn thành.
 */
@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private static final double SECONDS_PER_DAY = 86_400.0;

    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;

    public void pushScore(String jlptLevel, Long userId, int totalScore, int timeSpentSeconds) {
        double score = totalScore + (1 - timeSpentSeconds / SECONDS_PER_DAY);
        redisTemplate.opsForZSet().add(leaderboardKey(jlptLevel), String.valueOf(userId), score);
    }

    public List<LeaderboardEntryResponse> getTop(String jlptLevel, int limit) {
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                .reverseRangeWithScores(leaderboardKey(jlptLevel), 0, limit - 1L);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        List<Long> userIds = tuples.stream()
                .map(t -> Long.valueOf(t.getValue()))
                .toList();
        // Batch-fetch username 1 lần duy nhất thay vì lặp query từng user (tránh N+1 Query).
        Map<Long, String> usernameById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));

        List<LeaderboardEntryResponse> result = new ArrayList<>();
        int rank = 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            Long userId = Long.valueOf(tuple.getValue());
            result.add(LeaderboardEntryResponse.builder()
                    .rank(rank++)
                    .userId(userId)
                    .username(usernameById.getOrDefault(userId, "unknown"))
                    .score(tuple.getScore() == null ? 0 : tuple.getScore())
                    .build());
        }
        return result;
    }

    public MyRankResponse getMyRank(String jlptLevel, Long userId) {
        String member = String.valueOf(userId);
        Long reverseRank = redisTemplate.opsForZSet().reverseRank(leaderboardKey(jlptLevel), member);
        Double score = redisTemplate.opsForZSet().score(leaderboardKey(jlptLevel), member);

        return MyRankResponse.builder()
                .userId(userId)
                .rank(reverseRank != null ? (int) (reverseRank + 1) : null)
                .score(score)
                .build();
    }

    private String leaderboardKey(String jlptLevel) {
        return "leaderboard:" + jlptLevel;
    }
}
