-- Ôn ngữ pháp theo lịch: mỗi điểm ngữ pháp người học thêm vào là một thẻ, xếp lịch bằng cùng bộ tính với thẻ từ vựng
-- (SM-2 và FSRS luôn được cập nhật cùng nhau - xem CardScheduler).
CREATE TABLE user_grammar_srs (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT        NOT NULL,
    grammar_point_id     BIGINT        NOT NULL,
    repetition_count     INTEGER       NOT NULL DEFAULT 0,
    easiness_factor      NUMERIC(4, 2) NOT NULL DEFAULT 2.50,
    review_interval_days INTEGER       NOT NULL DEFAULT 0,
    next_review_at       TIMESTAMP     NOT NULL,
    last_reviewed_at     TIMESTAMP,                -- NULL = vừa thêm, chưa ôn lần nào
    lapse_count          INTEGER       NOT NULL DEFAULT 0,
    stability            DOUBLE PRECISION,         -- NULL cùng lúc với difficulty: chưa ôn lần nào
    difficulty           DOUBLE PRECISION,
    version              BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_user_grammar UNIQUE (user_id, grammar_point_id),
    CONSTRAINT fk_grammar_srs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_grammar_srs_point FOREIGN KEY (grammar_point_id) REFERENCES grammar_points(id) ON DELETE CASCADE,
    CONSTRAINT ck_grammar_srs_repetition CHECK (repetition_count >= 0),
    CONSTRAINT ck_grammar_srs_easiness CHECK (easiness_factor >= 1.30),
    CONSTRAINT ck_grammar_srs_interval CHECK (review_interval_days >= 0),
    CONSTRAINT ck_grammar_srs_lapses CHECK (lapse_count >= 0),
    CONSTRAINT ck_grammar_srs_memory CHECK ((stability IS NULL) = (difficulty IS NULL)),
    CONSTRAINT ck_grammar_srs_stability CHECK (stability > 0),
    CONSTRAINT ck_grammar_srs_difficulty CHECK (difficulty BETWEEN 1 AND 10)
);
CREATE INDEX idx_grammar_srs_user_next_review ON user_grammar_srs (user_id, next_review_at);

-- Lịch sử ôn ngữ pháp, tách khỏi review_logs: bộ tối ưu FSRS và thống kê tỉ lệ nhớ của từ vựng không lẫn ngữ pháp.
-- question_id: câu hỏi đã dùng để hỏi điểm này; NULL = thẻ lật (điểm chưa có câu đã duyệt) hoặc câu đã bị xoá.
CREATE TABLE grammar_review_logs (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT      NOT NULL,
    grammar_point_id BIGINT      NOT NULL,
    question_id      BIGINT,
    chosen_option    VARCHAR(1),
    correct          BOOLEAN     NOT NULL,
    rating           SMALLINT    NOT NULL,
    response_ms      INTEGER,
    state_before     VARCHAR(12) NOT NULL,
    retrievability   DOUBLE PRECISION,
    reviewed_at      TIMESTAMP   NOT NULL,
    CONSTRAINT fk_grammar_log_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_grammar_log_point FOREIGN KEY (grammar_point_id) REFERENCES grammar_points(id) ON DELETE CASCADE,
    CONSTRAINT fk_grammar_log_question FOREIGN KEY (question_id) REFERENCES exam_questions(id) ON DELETE SET NULL,
    CONSTRAINT ck_grammar_log_rating CHECK (rating BETWEEN 1 AND 4),
    CONSTRAINT ck_grammar_log_option CHECK (chosen_option IN ('A', 'B', 'C', 'D')),
    CONSTRAINT ck_grammar_log_state_before CHECK (state_before IN ('NEW', 'REVIEW', 'RELEARNING')),
    CONSTRAINT ck_grammar_log_response CHECK (response_ms >= 0)
);
CREATE INDEX idx_grammar_logs_user_time ON grammar_review_logs (user_id, reviewed_at);
CREATE INDEX idx_grammar_logs_question ON grammar_review_logs (question_id);
