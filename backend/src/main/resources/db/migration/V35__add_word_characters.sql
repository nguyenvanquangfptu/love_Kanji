-- Các chữ Hán của từng từ trong bảng kanji, theo thứ tự trong từ, kèm âm Hán Việt của chữ đó TRONG từ này
-- (行 là HÀNG trong 銀行 nhưng là HÀNH trong 行動). Tách từ cột han_viet (mỗi chữ Hán một âm tiết); 々 không có dòng
-- riêng. Dùng để hiện Hán Việt từng chữ và tìm các từ cùng chữ.
CREATE TABLE word_characters (
    word_id   BIGINT      NOT NULL,
    position  INTEGER     NOT NULL,
    character VARCHAR(1)  NOT NULL,
    han_viet  VARCHAR(20) NOT NULL,
    PRIMARY KEY (word_id, position),
    CONSTRAINT fk_word_characters_word FOREIGN KEY (word_id) REFERENCES kanji(id) ON DELETE CASCADE,
    CONSTRAINT ck_word_characters_position CHECK (position >= 0),
    CONSTRAINT ck_word_characters_character CHECK (char_length(character) = 1),
    CONSTRAINT ck_word_characters_han_viet CHECK (han_viet <> '')
);

-- Tìm các từ cùng một chữ.
CREATE INDEX idx_word_characters_character ON word_characters (character);
