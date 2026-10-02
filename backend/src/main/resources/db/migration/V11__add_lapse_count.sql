-- Số lần quên một từ đã thuộc: "Quên" khi thẻ đang ôn bình thường, tức đã nhớ lại được ít nhất một lần kể từ lần quên
-- trước. Quên tiếp trong lúc đang học lại không tính thêm. Quên đủ app.srs.hard-word-lapses lần (mặc định 6) là "từ khó".
ALTER TABLE user_kanji_srs ADD COLUMN lapse_count INT NOT NULL DEFAULT 0;

-- Tính lại từ lịch sử ôn đã ghi từ V10.
UPDATE user_kanji_srs s
SET lapse_count = l.lapses
FROM (SELECT user_id, kanji_id, COUNT(*) AS lapses
      FROM review_logs
      WHERE rating = 1 AND scheduled AND state_before = 'REVIEW'
      GROUP BY user_id, kanji_id) l
WHERE s.user_id = l.user_id AND s.kanji_id = l.kanji_id;

-- Danh sách và số từ khó của một người.
CREATE INDEX idx_user_srs_lapses ON user_kanji_srs (user_id, lapse_count);
