-- Tham số FSRS tối ưu riêng cho từng người từ lịch sử ôn của chính họ (FsrsOptimizer, chạy hằng tuần).
-- Chưa có dòng nào = dùng tham số mặc định của FSRS.
CREATE TABLE user_fsrs_parameters (
    user_id       BIGINT PRIMARY KEY,
    fsrs_version  VARCHAR(10) NOT NULL, -- bộ tham số chỉ đúng với phiên bản FSRS đã tối ưu ra nó
    parameters    JSONB       NOT NULL, -- mảng 21 tham số FSRS-6
    first_reviews INT         NOT NULL, -- số từ (lần học đầu + lần ôn kế tiếp) đã dùng để tối ưu
    optimized_at  TIMESTAMP   NOT NULL,
    CONSTRAINT fk_fsrs_parameters_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
