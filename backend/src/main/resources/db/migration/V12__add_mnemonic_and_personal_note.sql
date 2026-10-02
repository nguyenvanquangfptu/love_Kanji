-- Mẹo nhớ chung của một từ (AI sinh dựa trên âm Hán Việt / hình chữ): sinh một lần rồi dùng cho mọi người.
ALTER TABLE kanji ADD COLUMN mnemonic TEXT;

-- Cách nhớ riêng người học tự ghi cho một từ trong lịch ôn của mình.
ALTER TABLE user_kanji_srs ADD COLUMN personal_note TEXT;
