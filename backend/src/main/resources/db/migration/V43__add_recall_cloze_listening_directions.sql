-- Ba hướng hỏi mới, chỉ có trong nhật ký ôn tập (không phải kỹ năng câu thi, nên ck_exam_questions_skill giữ nguyên):
--   RECALL_WORD: thẻ ôn hỏi ngược - cho nghĩa, người học tự nhớ ra từ (source FLASHCARD)
--   CLOZE:       trắc nghiệm điền từ vào câu ví dụ
--   LISTENING:   trắc nghiệm nghe cách đọc rồi chọn nghĩa
ALTER TABLE review_logs DROP CONSTRAINT ck_review_logs_direction;
ALTER TABLE review_logs ADD CONSTRAINT ck_review_logs_direction CHECK (direction IN ('KANJI_TO_READING',
    'READING_TO_KANJI', 'MEANING', 'TYPE_READING', 'RECALL_WORD', 'CLOZE', 'LISTENING'));
