-- Thuật toán lịch ôn người học chọn: SM-2 (mặc định, như trước) hoặc FSRS; và tỉ lệ nhớ mong muốn khi dùng FSRS.
ALTER TABLE user_learning_profiles ADD COLUMN scheduler VARCHAR(10) NOT NULL DEFAULT 'SM2';
ALTER TABLE user_learning_profiles ADD COLUMN desired_retention NUMERIC(3, 2) NOT NULL DEFAULT 0.90;
