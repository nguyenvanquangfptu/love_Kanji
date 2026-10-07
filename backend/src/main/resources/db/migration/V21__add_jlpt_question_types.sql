-- Dạng câu theo đề JLPT (問題1 漢字読み, 問題2 表記...) và trạng thái duyệt: chỉ câu đã duyệt mới được lấy vào đề.
ALTER TABLE exam_questions ADD COLUMN question_type VARCHAR(20); -- KANJI_READING | ORTHOGRAPHY | CONTEXT | PARAPHRASE | USAGE
                                                                -- | GRAMMAR_FORM | SENTENCE_ORDER | TEXT_GRAMMAR; NULL = chỉ dùng cho thi nhanh
ALTER TABLE exam_questions ADD COLUMN status VARCHAR(10) NOT NULL DEFAULT 'APPROVED'; -- DRAFT | APPROVED | REJECTED | RETIRED

-- Câu đọc/viết có câu ví dụ gạch chân đã đúng kiểu đề thật. Câu hỏi nghĩa (đáp án tiếng Việt) và câu mẫu kiểu cũ
-- ("Âm on'yomi của Kanji 火 là gì?") không có trong đề JLPT nên để trống.
UPDATE exam_questions SET question_type = 'KANJI_READING' WHERE skill = 'KANJI_TO_READING' AND sentence IS NOT NULL;
UPDATE exam_questions SET question_type = 'ORTHOGRAPHY' WHERE skill = 'READING_TO_KANJI' AND sentence IS NOT NULL;

CREATE INDEX idx_exam_questions_level_type ON exam_questions (jlpt_level, question_type, status);
