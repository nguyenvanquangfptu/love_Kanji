package com.kanjimastery.backend.service;

import com.kanjimastery.backend.dto.JlptLeaderboardEntryResponse;
import com.kanjimastery.backend.dto.LeaderboardEntryResponse;
import com.kanjimastery.backend.dto.MyRankResponse;
import com.kanjimastery.backend.model.User;
import com.kanjimastery.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
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

    /**
     * Đưa một buổi làm đề JLPT trọn vẹn lên bảng xếp hạng đề JLPT của cấp độ. Mỗi người học giữ buổi thi tốt nhất: tỉ lệ
     * đúng cao hơn, bằng nhau thì làm nhanh hơn. Gọi sau khi buổi thi đã được lưu là hoàn thành.
     */
    public void pushJlptResult(JlptExamService.CompletedSitting result) {
        String member = String.valueOf(result.userId());
        double score = jlptRankingScore(result.correct(), result.total(), result.seconds());
        Double best = redisTemplate.opsForZSet().score(jlptKey(result.level().name()), member);
        if (best != null && best >= score) {
            return;
        }
        redisTemplate.opsForZSet().add(jlptKey(result.level().name()), member, score);
        redisTemplate.opsForHash().put(jlptDetailKey(result.level().name()), member,
                result.correct() + "," + result.total() + "," + result.seconds());
    }

    public List<JlptLeaderboardEntryResponse> getJlptTop(String jlptLevel, int limit) {
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                .reverseRangeWithScores(jlptKey(jlptLevel), 0, limit - 1L);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        List<Object> members = tuples.stream().map(tuple -> (Object) tuple.getValue()).toList();
        List<Object> details = redisTemplate.opsForHash().multiGet(jlptDetailKey(jlptLevel), members);
        Map<Long, String> usernameById = userRepository.findAllById(members.stream()
                        .map(member -> Long.valueOf((String) member)).toList()).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));
        List<JlptLeaderboardEntryResponse> result = new ArrayList<>();
        for (int index = 0; index < members.size(); index++) {
            Long userId = Long.valueOf((String) members.get(index));
            result.add(jlptEntry(index + 1, userId, usernameById.getOrDefault(userId, "unknown"),
                    (String) details.get(index)));
        }
        return result;
    }

    /** Dòng của người học trên bảng xếp hạng đề JLPT; hạng null khi chưa có buổi thi trọn vẹn nào. */
    public JlptLeaderboardEntryResponse getMyJlptRank(String jlptLevel, Long userId) {
        String member = String.valueOf(userId);
        Long reverseRank = redisTemplate.opsForZSet().reverseRank(jlptKey(jlptLevel), member);
        if (reverseRank == null) {
            return new JlptLeaderboardEntryResponse(null, userId, null, null, null, null, null);
        }
        Object detail = redisTemplate.opsForHash().get(jlptDetailKey(jlptLevel), member);
        return jlptEntry((int) (reverseRank + 1), userId, null, (String) detail);
    }

    /** Điểm xếp hạng: tỉ lệ đúng (phần vạn) là phần nguyên, phần lẻ lớn hơn khi làm nhanh hơn. */
    static double jlptRankingScore(int correct, int total, int seconds) {
        long accuracy = Math.round(correct * 10_000.0 / total);
        return accuracy + (1 - Math.min(seconds, SECONDS_PER_DAY) / (SECONDS_PER_DAY + 1));
    }

    private static JlptLeaderboardEntryResponse jlptEntry(int rank, Long userId, String username, String detail) {
        if (detail == null) {
            return new JlptLeaderboardEntryResponse(rank, userId, username, null, null, null, null);
        }
        int[] parts = Arrays.stream(detail.split(",")).mapToInt(Integer::parseInt).toArray();
        return new JlptLeaderboardEntryResponse(rank, userId, username,
                JlptExamService.estimatedScore(parts[0], parts[1]), parts[0], parts[1], parts[2]);
    }

    private static String jlptKey(String jlptLevel) {
        return "leaderboard:jlpt:" + jlptLevel;
    }

    private static String jlptDetailKey(String jlptLevel) {
        return "leaderboard:jlpt:" + jlptLevel + ":detail";
    }
}
