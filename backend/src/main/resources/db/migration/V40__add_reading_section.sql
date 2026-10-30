-- Phần Đọc hiểu (読解) của đề JLPT: mỗi bài đọc là một đoạn văn trong exam_passages kèm các câu hỏi của nó, như đoạn
-- văn 文章の文法 của phần Ngữ pháp. Cột section ghi đoạn văn thuộc phần nào để ghép đề không lấy nhầm bài của phần kia.
-- Câu hỏi của bài đọc dùng blank_no làm thứ tự câu trong bài (1..n), không có chỗ trống 【n】 trong đoạn văn.

-- Mọi đoạn văn đã có là đoạn 文章の文法. Không đặt DEFAULT: chỗ tạo đoạn văn nào quên gán phần thi thì lỗi ngay khi ghi.
ALTER TABLE exam_passages ADD COLUMN section VARCHAR(12);
UPDATE exam_passages SET section = 'GRAMMAR';
ALTER TABLE exam_passages ALTER COLUMN section SET NOT NULL;
-- Phần Từ vựng không có đoạn văn.
ALTER TABLE exam_passages ADD CONSTRAINT ck_exam_passages_section CHECK (section IN ('GRAMMAR', 'READING'));

-- Ghép đề lọc theo cấp độ + phần thi + trạng thái.
DROP INDEX idx_exam_passages_level_status;
CREATE INDEX idx_exam_passages_level_section_status ON exam_passages (jlpt_level, section, status);

-- Bốn dạng câu của phần Đọc hiểu.
ALTER TABLE exam_questions DROP CONSTRAINT ck_exam_questions_question_type;
ALTER TABLE exam_questions ADD CONSTRAINT ck_exam_questions_question_type CHECK (question_type IN ('KANJI_READING', 'ORTHOGRAPHY', 'CONTEXT', 'PARAPHRASE', 'USAGE', 'GRAMMAR_FORM', 'SENTENCE_ORDER', 'TEXT_GRAMMAR', 'READING_SHORT', 'READING_MID', 'READING_LONG', 'INFO_SEARCH'));

-- Lượt thi và buổi thi có thêm phần READING.
ALTER TABLE user_exam_attempts DROP CONSTRAINT ck_user_exam_attempts_section;
ALTER TABLE user_exam_attempts ADD CONSTRAINT ck_user_exam_attempts_section CHECK (section IN ('VOCABULARY', 'GRAMMAR', 'READING'));
ALTER TABLE exam_sittings DROP CONSTRAINT ck_exam_sittings_sections;
ALTER TABLE exam_sittings ADD CONSTRAINT ck_exam_sittings_sections CHECK (sections ~ '^(VOCABULARY|GRAMMAR|READING)(,(VOCABULARY|GRAMMAR|READING))*$');
