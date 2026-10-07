-- Khoá lạc quan cho thẻ ôn: hai lần chấm cùng một thẻ cùng lúc (bấm hai lần, hai tab) thì lần sau bị từ chối thay vì
-- ghi đè lịch ôn của lần trước. DEFAULT 0 vì các câu INSERT native trong UserKanjiSrsRepository không liệt kê cột này.
ALTER TABLE user_kanji_srs ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
