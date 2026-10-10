-- Hướng hỏi mới TYPE_READING: cho chữ Hán, người học tự gõ cách đọc. Chỉ có trong nhật ký ôn tập, không phải kỹ năng
-- câu thi, nên ck_exam_questions_skill giữ nguyên.
ALTER TABLE review_logs DROP CONSTRAINT ck_review_logs_direction;
ALTER TABLE review_logs ADD CONSTRAINT ck_review_logs_direction CHECK (direction IN ('KANJI_TO_READING', 'READING_TO_KANJI', 'MEANING', 'TYPE_READING'));
