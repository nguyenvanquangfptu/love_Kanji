-- Đoạn văn của 問題3 文章の文法: một đoạn có nhiều chỗ trống, mỗi chỗ trống là một câu hỏi 4 lựa chọn. Chỗ trống trong
-- nội dung đánh dấu 【1】【2】... theo blank_no của câu hỏi. Ghép đề thì lấy trọn một đoạn cùng mọi câu hỏi của nó;
-- duyệt thì duyệt cả đoạn một lần (trạng thái câu hỏi đi theo đoạn văn).
CREATE TABLE exam_passages (
    id          BIGSERIAL PRIMARY KEY,
    jlpt_level  VARCHAR(5)   NOT NULL,
    title       VARCHAR(200),
    content     TEXT         NOT NULL,
    status      VARCHAR(10)  NOT NULL DEFAULT 'DRAFT',  -- DRAFT | APPROVED | REJECTED | RETIRED
    source      VARCHAR(10)  NOT NULL DEFAULT 'MANUAL', -- MANUAL | AI
    flag        VARCHAR(20),
    review_note TEXT,
    reviewed_at TIMESTAMP,
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_exam_passages_level_status ON exam_passages (jlpt_level, status);

ALTER TABLE exam_questions ADD COLUMN passage_id BIGINT;
ALTER TABLE exam_questions ADD CONSTRAINT fk_exam_question_passage
    FOREIGN KEY (passage_id) REFERENCES exam_passages(id) ON DELETE CASCADE;
ALTER TABLE exam_questions ADD COLUMN blank_no INT; -- chỗ trống 【n】 trong đoạn văn
CREATE INDEX idx_exam_questions_passage ON exam_questions (passage_id);
