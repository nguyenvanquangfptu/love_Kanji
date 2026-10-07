-- Buổi thi theo đề JLPT: nhiều phần (Từ vựng, Ngữ pháp), mỗi phần là một lượt thi riêng có giờ riêng - dùng lại toàn bộ
-- cơ chế đếm giờ, tự nộp, chấm điểm của lượt thi.
CREATE TABLE exam_sittings (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    jlpt_level  VARCHAR(5)  NOT NULL,
    sections    VARCHAR(40) NOT NULL,                        -- các phần đã chọn, theo thứ tự: VOCABULARY,GRAMMAR
    status      VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',  -- IN_PROGRESS | COMPLETED | ABANDONED
    started_at  TIMESTAMP   NOT NULL,
    finished_at TIMESTAMP,
    CONSTRAINT fk_exam_sitting_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_exam_sittings_user ON exam_sittings (user_id, started_at);

ALTER TABLE user_exam_attempts ADD COLUMN sitting_id BIGINT;
ALTER TABLE user_exam_attempts ADD CONSTRAINT fk_attempt_sitting FOREIGN KEY (sitting_id) REFERENCES exam_sittings(id) ON DELETE CASCADE;
ALTER TABLE user_exam_attempts ADD COLUMN section VARCHAR(12);      -- VOCABULARY | GRAMMAR; NULL = thi nhanh
ALTER TABLE user_exam_attempts ADD COLUMN duration_seconds INT;    -- thời gian làm bài của lượt; NULL = app.exam.duration-seconds
-- Mỗi phần của buổi thi chỉ một lượt thi (chặn bấm "bắt đầu phần tiếp" hai lần); thi nhanh có sitting_id NULL nên
-- không bị ràng buộc.
CREATE UNIQUE INDEX uq_attempts_sitting_section ON user_exam_attempts (sitting_id, section);
