-- Trạng thái trí nhớ FSRS-6 của từng thẻ, tính song song với SM-2 ở mỗi lần ôn: độ ổn định (số ngày để xác suất nhớ
-- còn 90%) và độ khó (1-10). NULL = thẻ chưa được ôn lần nào từ khi có FSRS, ước lượng từ trạng thái SM-2 khi cần.
ALTER TABLE user_kanji_srs ADD COLUMN stability DOUBLE PRECISION;
ALTER TABLE user_kanji_srs ADD COLUMN difficulty DOUBLE PRECISION;

-- Xác suất nhớ FSRS dự đoán ngay lúc người học trả lời - để so dự đoán với thực tế.
ALTER TABLE review_logs ADD COLUMN retrievability DOUBLE PRECISION;
