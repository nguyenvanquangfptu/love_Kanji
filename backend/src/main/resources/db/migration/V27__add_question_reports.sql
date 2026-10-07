-- Người học báo lỗi câu hỏi từ trang xem lại bài thi: mỗi người một báo cáo cho mỗi câu (báo lại thì cập nhật lý do).
-- Câu đã duyệt bị nhiều người báo (báo cáo đang mở) thì tự rút về chờ duyệt; người duyệt đổi trạng thái câu thì các
-- báo cáo đang mở được đóng (RESOLVED), hoặc bỏ qua (DISMISSED) nếu câu không sai.
CREATE TABLE exam_question_reports (
    id          BIGSERIAL PRIMARY KEY,
    question_id BIGINT      NOT NULL REFERENCES exam_questions (id) ON DELETE CASCADE,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    reason      VARCHAR(20) NOT NULL,                -- WRONG_ANSWER | AMBIGUOUS | UNCLEAR | OTHER
    note        VARCHAR(500),
    status      VARCHAR(10) NOT NULL DEFAULT 'OPEN', -- OPEN | RESOLVED | DISMISSED
    created_at  TIMESTAMP   NOT NULL DEFAULT now(),
    resolved_at TIMESTAMP,
    CONSTRAINT uq_question_report_user UNIQUE (question_id, user_id)
);
CREATE INDEX idx_question_reports_status ON exam_question_reports (status, question_id);
