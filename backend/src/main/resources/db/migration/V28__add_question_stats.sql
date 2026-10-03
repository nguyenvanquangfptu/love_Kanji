-- Thống kê từng câu từ kết quả thi thật (job phân tích câu), tính trên các lượt làm từ lần duyệt câu gần nhất: số lượt
-- trả lời (không tính bỏ trống), tỉ lệ đúng, và độ phân biệt - tỉ lệ đúng của nhóm làm tốt các câu khác trừ nhóm làm
-- kém (27% mỗi đầu). Câu đã duyệt mà độ phân biệt âm thường là sai đáp án hoặc có hai đáp án.
CREATE TABLE exam_question_stats (
    question_id    BIGINT PRIMARY KEY REFERENCES exam_questions (id) ON DELETE CASCADE,
    responses      INT              NOT NULL,
    correct_rate   DOUBLE PRECISION NOT NULL,
    discrimination DOUBLE PRECISION, -- null khi không chia được hai nhóm
    computed_at    TIMESTAMP        NOT NULL
);
