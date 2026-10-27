-- Trạng thái thẻ ngay trước mỗi lần trả lời, bổ sung cho state_before/ef_before/interval_before (V10): đủ để khôi phục
-- thẻ khi người học hoàn tác một lần chấm thẻ bấm nhầm.
-- card_existed_before: FALSE = từ chưa có trong lịch ôn trước lần trả lời này; NULL = dòng ghi trước V36, không hoàn
-- tác được.
ALTER TABLE review_logs ADD COLUMN card_existed_before  BOOLEAN;
ALTER TABLE review_logs ADD COLUMN repetition_before    INTEGER;
ALTER TABLE review_logs ADD COLUMN lapses_before        INTEGER;
ALTER TABLE review_logs ADD COLUMN next_review_before   TIMESTAMP;
ALTER TABLE review_logs ADD COLUMN last_reviewed_before TIMESTAMP;
ALTER TABLE review_logs ADD COLUMN stability_before     DOUBLE PRECISION;
ALTER TABLE review_logs ADD COLUMN difficulty_before    DOUBLE PRECISION;

ALTER TABLE review_logs ADD CONSTRAINT ck_review_logs_lapses_before CHECK (lapses_before >= 0);
-- Thẻ đã có thì phải có đủ trạng thái để khôi phục.
ALTER TABLE review_logs ADD CONSTRAINT ck_review_logs_card_before CHECK (
    card_existed_before IS NOT TRUE
    OR (repetition_before IS NOT NULL AND lapses_before IS NOT NULL AND next_review_before IS NOT NULL));
