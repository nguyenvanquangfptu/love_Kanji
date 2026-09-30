-- 1. Bảng Người dùng
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'ROLE_USER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Bảng Từ điển Hán tự
CREATE TABLE kanji (
    id BIGSERIAL PRIMARY KEY,
    character VARCHAR(5) NOT NULL UNIQUE,
    han_viet VARCHAR(50) NOT NULL,
    onyomi VARCHAR(100),
    kunyomi VARCHAR(100),
    stroke_count INT NOT NULL,
    jlpt_level VARCHAR(5) NOT NULL,       -- N5, N4, N3, N2, N1
    meaning TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_kanji_level ON kanji(jlpt_level);

-- 3. Bảng Tiến độ học SRS (SuperMemo SM-2)
CREATE TABLE user_kanji_srs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    kanji_id BIGINT NOT NULL,
    repetition_count INT DEFAULT 0,
    easiness_factor NUMERIC(4, 2) DEFAULT 2.50,
    review_interval_days INT DEFAULT 0,
    next_review_at TIMESTAMP NOT NULL,
    last_reviewed_at TIMESTAMP,
    CONSTRAINT uk_user_kanji UNIQUE (user_id, kanji_id),
    CONSTRAINT fk_srs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_srs_kanji FOREIGN KEY (kanji_id) REFERENCES kanji(id) ON DELETE CASCADE
);

-- Composite index phục vụ query thẻ đến hạn ôn tập
CREATE INDEX idx_user_next_review ON user_kanji_srs (user_id, next_review_at);

-- 4. Bảng Ngân hàng câu hỏi trắc nghiệm
CREATE TABLE exam_questions (
    id BIGSERIAL PRIMARY KEY,
    jlpt_level VARCHAR(5) NOT NULL,
    question_text TEXT NOT NULL,
    option_a VARCHAR(255) NOT NULL,
    option_b VARCHAR(255) NOT NULL,
    option_c VARCHAR(255) NOT NULL,
    option_d VARCHAR(255) NOT NULL,
    correct_option CHAR(1) NOT NULL,     -- A, B, C, D
    explanation TEXT
);

-- 5. Bảng Lượt thi của thí sinh
CREATE TABLE user_exam_attempts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    jlpt_level VARCHAR(5) NOT NULL,
    total_score INT DEFAULT 0,
    time_spent_seconds INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'IN_PROGRESS', -- IN_PROGRESS, COMPLETED, TIMEOUT
    started_at TIMESTAMP NOT NULL,
    submitted_at TIMESTAMP,
    CONSTRAINT fk_attempt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 6. Bảng chi tiết từng câu trả lời (phục vụ tính năng "xem lại bài làm / câu sai")
CREATE TABLE user_exam_answers (
    id BIGSERIAL PRIMARY KEY,
    attempt_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    selected_option CHAR(1),              -- NULL nếu thí sinh bỏ trống câu này
    is_correct BOOLEAN NOT NULL DEFAULT FALSE,
    answered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_attempt_question UNIQUE (attempt_id, question_id),
    CONSTRAINT fk_answer_attempt FOREIGN KEY (attempt_id) REFERENCES user_exam_attempts(id) ON DELETE CASCADE,
    CONSTRAINT fk_answer_question FOREIGN KEY (question_id) REFERENCES exam_questions(id) ON DELETE CASCADE
);

CREATE INDEX idx_answer_attempt ON user_exam_answers(attempt_id);
