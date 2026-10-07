-- Danh sách điểm ngữ pháp theo cấp độ (N5, N4 theo bài Minna như kho từ vựng; N3 không theo bài). Câu thi ngữ pháp
-- gắn với điểm ngữ pháp nó kiểm tra: sinh câu theo từng điểm, biết điểm nào còn thiếu câu, sau này chẩn đoán điểm yếu.
CREATE TABLE grammar_points (
    id             BIGSERIAL PRIMARY KEY,
    jlpt_level     VARCHAR(5)   NOT NULL,
    lesson         VARCHAR(20),                  -- bài trong giáo trình (N4-26...); NULL = không theo bài
    pattern        VARCHAR(100) NOT NULL,        -- 〜てから
    connection     VARCHAR(200),                 -- Vて + から
    meaning_vi     TEXT         NOT NULL,
    explanation_vi TEXT,
    CONSTRAINT uq_grammar_point UNIQUE (jlpt_level, pattern)
);

CREATE TABLE exam_question_grammar (
    question_id      BIGINT NOT NULL,
    grammar_point_id BIGINT NOT NULL,
    PRIMARY KEY (question_id, grammar_point_id),
    CONSTRAINT fk_question_grammar_question FOREIGN KEY (question_id) REFERENCES exam_questions(id) ON DELETE CASCADE,
    CONSTRAINT fk_question_grammar_point FOREIGN KEY (grammar_point_id) REFERENCES grammar_points(id) ON DELETE CASCADE
);
CREATE INDEX idx_question_grammar_point ON exam_question_grammar (grammar_point_id);
