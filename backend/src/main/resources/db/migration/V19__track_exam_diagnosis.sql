-- Lúc kết quả bài thi được đưa vào ôn tập (ExamDiagnosisService); NULL = chưa. Mỗi bài thi chỉ được đưa vào một lần.
ALTER TABLE user_exam_attempts ADD COLUMN diagnosed_at TIMESTAMP;
