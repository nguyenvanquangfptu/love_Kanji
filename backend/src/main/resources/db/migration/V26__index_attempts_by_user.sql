-- Lượt thi theo người học: ghép đề JLPT tìm các câu, đoạn văn người học đã gặp (ưu tiên câu chưa gặp), và tìm các điểm
-- ngữ pháp người học hay làm sai.
CREATE INDEX idx_attempts_user ON user_exam_attempts (user_id, started_at);
