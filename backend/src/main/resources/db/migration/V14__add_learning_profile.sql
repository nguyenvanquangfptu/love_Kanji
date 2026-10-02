-- Mục tiêu học của từng người: cấp độ JLPT nhắm tới, ngày thi, thời gian ôn mỗi ngày và số từ mới tự chọn.
-- Chưa có dòng nào = chưa đặt mục tiêu, app dùng mặc định trong app.srs.
CREATE TABLE user_learning_profiles (
    user_id           BIGINT PRIMARY KEY,
    target_level      VARCHAR(5),
    exam_date         DATE,
    daily_minutes     INT       NOT NULL,
    new_words_per_day INT,                  -- người học tự đặt; NULL = để app tính
    updated_at        TIMESTAMP NOT NULL,
    CONSTRAINT fk_learning_profile_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
