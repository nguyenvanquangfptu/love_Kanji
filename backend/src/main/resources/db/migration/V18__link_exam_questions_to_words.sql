-- Mỗi câu thi gắn với kỹ năng và các từ vựng nó kiểm tra, để kết quả thi chỉ ra người học yếu kỹ năng nào, từ nào.
ALTER TABLE exam_questions ADD COLUMN skill VARCHAR(20);                            -- như hướng hỏi trắc nghiệm: KANJI_TO_READING | READING_TO_KANJI | MEANING
ALTER TABLE exam_questions ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'MANUAL'; -- MANUAL | GENERATED

CREATE TABLE exam_question_kanji (
    question_id BIGINT NOT NULL,
    kanji_id    BIGINT NOT NULL,
    PRIMARY KEY (question_id, kanji_id),
    CONSTRAINT fk_exam_question_kanji_question FOREIGN KEY (question_id) REFERENCES exam_questions(id) ON DELETE CASCADE,
    CONSTRAINT fk_exam_question_kanji_kanji FOREIGN KEY (kanji_id) REFERENCES kanji(id) ON DELETE CASCADE
);
CREATE INDEX idx_exam_question_kanji_kanji ON exam_question_kanji (kanji_id);

-- 20 câu mẫu ở V3: kỹ năng và chữ Hán mỗi câu hỏi tới. Câu đếm nét tính vào kỹ năng viết.
CREATE TEMPORARY TABLE seed_question_words (question_text TEXT, skill VARCHAR(20), word VARCHAR(50));
INSERT INTO seed_question_words (question_text, skill, word) VALUES
('Kanji 水 có nghĩa là gì?', 'MEANING', '水'),
('Kanji nào có nghĩa là "núi"?', 'MEANING', '山'),
('Âm on''yomi của Kanji 火 là gì?', 'KANJI_TO_READING', '火'),
('Kanji 人 có âm on''yomi nào sau đây?', 'KANJI_TO_READING', '人'),
('Kanji 大 có nghĩa là gì?', 'MEANING', '大'),
('Kanji nào có nghĩa là "nhỏ"?', 'MEANING', '小'),
('Kanji 木 có kun''yomi là gì?', 'KANJI_TO_READING', '木'),
('Kanji 金 có nghĩa là gì?', 'MEANING', '金'),
('Kanji 学 có nghĩa là gì?', 'MEANING', '学'),
('Kanji nào có nghĩa là "trường học"?', 'MEANING', '校'),
('Trong từ 先生 (giáo viên), Kanji nào có nghĩa là "trước"?', 'MEANING', '先'),
('Kanji 食 có kun''yomi là gì?', 'KANJI_TO_READING', '食'),
('Kanji nào có nghĩa là "uống"?', 'MEANING', '飲'),
('Kanji 行 (kun''yomi い-く) có nghĩa là gì?', 'MEANING', '行'),
('Kanji 来 có nghĩa là gì?', 'MEANING', '来'),
('Động từ "nhìn, thấy" (見る) được viết bằng Kanji nào?', 'READING_TO_KANJI', '見'),
('Kanji 耳 có nghĩa là gì?', 'MEANING', '耳'),
('Kanji 一 có bao nhiêu nét?', 'READING_TO_KANJI', '一'),
('Kanji nào có nghĩa là số "chín"?', 'MEANING', '九'),
('Kanji 今 có kun''yomi là gì?', 'KANJI_TO_READING', '今');

UPDATE exam_questions q
SET skill = s.skill
FROM seed_question_words s
WHERE q.question_text = s.question_text AND q.source = 'MANUAL';

-- Một chữ có thể có nhiều dòng trong kho (V9): lấy dòng có sớm nhất - dòng của bộ chữ Hán ở V2.
INSERT INTO exam_question_kanji (question_id, kanji_id)
SELECT q.id, MIN(k.id)
FROM exam_questions q
JOIN seed_question_words s ON s.question_text = q.question_text
JOIN kanji k ON k.character = s.word
WHERE q.source = 'MANUAL'
GROUP BY q.id;

DROP TABLE seed_question_words;
