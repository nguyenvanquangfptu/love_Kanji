-- Mỗi lần người học trả lời một từ (lật thẻ ôn tập, làm trắc nghiệm) là một dòng - nguồn dữ liệu để cá nhân hoá:
-- ngưỡng nhanh/chậm của từng người, từ hay sai, cặp hay nhầm, và sau này là tối ưu tham số FSRS riêng từng người.
CREATE TABLE review_logs (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT      NOT NULL,
    kanji_id        BIGINT      NOT NULL,
    source          VARCHAR(10) NOT NULL,                                -- FLASHCARD | QUIZ | EXAM
    direction       VARCHAR(20),                                         -- hướng hỏi của trắc nghiệm; NULL với thẻ ôn tập
    correct         BOOLEAN     NOT NULL,
    rating          SMALLINT    NOT NULL CHECK (rating BETWEEN 1 AND 4), -- 1 Quên, 2 Khó, 3 Nhớ, 4 Dễ
    response_ms     INT,                                                 -- NULL nếu không đo được hoặc người học rời máy
    chosen_answer   TEXT,                                                -- đáp án đã chọn trong trắc nghiệm
    state_before    VARCHAR(12) NOT NULL,                                -- NEW | REVIEW | RELEARNING
    ef_before       NUMERIC(4, 2),
    interval_before INT,
    scheduled       BOOLEAN     NOT NULL,                                -- lần trả lời này có được tính vào lịch ôn không
    reviewed_at     TIMESTAMP   NOT NULL,
    CONSTRAINT fk_review_log_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_review_log_kanji FOREIGN KEY (kanji_id) REFERENCES kanji(id) ON DELETE CASCADE
);

CREATE INDEX idx_review_logs_user_time ON review_logs (user_id, reviewed_at);
CREATE INDEX idx_review_logs_user_kanji ON review_logs (user_id, kanji_id, reviewed_at);
