-- Duyệt câu hỏi thi: cờ cảnh báo của bước kiểm tra tự động, ghi chú (lý do loại, chi tiết cảnh báo) và lúc duyệt.
ALTER TABLE exam_questions ADD COLUMN flag VARCHAR(20);   -- AMBIGUOUS | WRONG_ANSWER | ABOVE_LEVEL; NULL = không có cờ
ALTER TABLE exam_questions ADD COLUMN review_note TEXT;
ALTER TABLE exam_questions ADD COLUMN reviewed_at TIMESTAMP;
CREATE INDEX idx_exam_questions_status ON exam_questions (status, jlpt_level);
