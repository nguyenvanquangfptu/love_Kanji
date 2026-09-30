-- Phiên âm hiragana của cả từ (vd. 男性 -> だんせい), thay cho việc hiển thị onyomi/kunyomi rời rạc.
ALTER TABLE kanji ADD COLUMN reading VARCHAR(100);
