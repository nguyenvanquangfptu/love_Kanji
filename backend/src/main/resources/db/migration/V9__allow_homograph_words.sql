-- Một từ có thể có nhiều dòng, mỗi dòng một nghĩa/cách đọc ở bài khác nhau (vd. 開く あく ở bài 29, ひらく ở bài 27).
-- Ràng buộc "không trùng từ trong cùng một bài" được kiểm tra ở KanjiService vì tag nằm ở bảng kanji_tags.
ALTER TABLE kanji DROP CONSTRAINT IF EXISTS kanji_character_key;
CREATE INDEX IF NOT EXISTS idx_kanji_character ON kanji (character);
