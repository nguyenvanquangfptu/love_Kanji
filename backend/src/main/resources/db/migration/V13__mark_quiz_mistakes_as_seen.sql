-- Từ được thêm vào Ôn tập vì làm sai trong trắc nghiệm là từ đã gặp, không phải từ mới: lấy lần trả lời sai đầu tiên
-- làm lần ôn gần nhất, để giới hạn số từ mới mỗi ngày chỉ áp dụng cho từ chưa từng gặp.
UPDATE user_kanji_srs s
SET last_reviewed_at = l.first_mistake
FROM (SELECT user_id, kanji_id, MIN(reviewed_at) AS first_mistake
      FROM review_logs
      WHERE source = 'QUIZ' AND NOT correct
      GROUP BY user_id, kanji_id) l
WHERE s.user_id = l.user_id AND s.kanji_id = l.kanji_id AND s.last_reviewed_at IS NULL;
