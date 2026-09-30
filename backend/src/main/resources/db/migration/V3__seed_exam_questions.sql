-- Bộ 20 câu hỏi trắc nghiệm N5 mẫu, dựa trên bộ Kanji đã seed ở V2.
-- Có thể mở rộng thêm N4-N1 bằng migration V4, V5... sau này.

INSERT INTO exam_questions (jlpt_level, question_text, option_a, option_b, option_c, option_d, correct_option, explanation) VALUES
('N5', 'Kanji 水 có nghĩa là gì?', 'Lửa', 'Nước', 'Đất', 'Cây', 'B', '水 (thủy) nghĩa là "nước", kun''yomi đọc là みず.'),
('N5', 'Kanji nào có nghĩa là "núi"?', '川', '田', '山', '土', 'C', '山 (sơn) nghĩa là "núi", kun''yomi đọc là やま.'),
('N5', 'Âm on''yomi của Kanji 火 là gì?', 'スイ', 'カ', 'モク', 'ド', 'B', '火 (hỏa) có âm on''yomi là カ, kun''yomi là ひ.'),
('N5', 'Kanji 人 có âm on''yomi nào sau đây?', 'ジン / ニン', 'シ', 'ジョ', 'ダン', 'A', '人 (nhân) có 2 âm on''yomi phổ biến: ジン và ニン.'),
('N5', 'Kanji 大 có nghĩa là gì?', 'Nhỏ', 'Ở giữa', 'To, lớn', 'Phía dưới', 'C', '大 (đại) nghĩa là "to, lớn", kun''yomi おお-きい.'),
('N5', 'Kanji nào có nghĩa là "nhỏ"?', '小', '大', '中', '上', 'A', '小 (tiểu) nghĩa là "nhỏ", kun''yomi ちい-さい.'),
('N5', 'Kanji 木 có kun''yomi là gì?', 'みず', 'き', 'つち', 'やま', 'B', '木 (mộc) nghĩa là "cây, gỗ", kun''yomi đọc là き.'),
('N5', 'Kanji 金 có nghĩa là gì?', 'Đất', 'Vàng, tiền', 'Lửa', 'Nước', 'B', '金 (kim) nghĩa là "vàng, tiền", kun''yomi かね.'),
('N5', 'Kanji 学 có nghĩa là gì?', 'Trường học', 'Học', 'Tên', 'Sách', 'B', '学 (học) nghĩa là "học", kun''yomi まな-ぶ.'),
('N5', 'Kanji nào có nghĩa là "trường học"?', '学', '校', '生', '先', 'B', '校 (hiệu) nghĩa là "trường học", thường ghép thành 学校.'),
('N5', 'Trong từ 先生 (giáo viên), Kanji nào có nghĩa là "trước"?', '生', '名', '先', '本', 'C', '先 (tiên) nghĩa là "trước", kun''yomi さき.'),
('N5', 'Kanji 食 có kun''yomi là gì?', 'た-べる', 'の-む', 'い-く', 'く-る', 'A', '食 (thực) nghĩa là "ăn", kun''yomi た-べる.'),
('N5', 'Kanji nào có nghĩa là "uống"?', '食', '飲', '行', '来', 'B', '飲 (ẩm) nghĩa là "uống", kun''yomi の-む.'),
('N5', 'Kanji 行 (kun''yomi い-く) có nghĩa là gì?', 'Đến', 'Đi', 'Ăn', 'Uống', 'B', '行 (hành) đọc kun''yomi い-く nghĩa là "đi".'),
('N5', 'Kanji 来 có nghĩa là gì?', 'Đi', 'Đến', 'Nghe', 'Nhìn', 'B', '来 (lai) nghĩa là "đến", kun''yomi く-る.'),
('N5', 'Động từ "nhìn, thấy" (見る) được viết bằng Kanji nào?', '聞', '見', '耳', '口', 'B', '見 (kiến) nghĩa là "nhìn, thấy", kun''yomi み-る.'),
('N5', 'Kanji 耳 có nghĩa là gì?', 'Mắt', 'Tai', 'Miệng', 'Tay', 'B', '耳 (nhĩ) nghĩa là "tai", kun''yomi みみ.'),
('N5', 'Kanji 一 có bao nhiêu nét?', '1', '2', '3', '4', 'A', '一 (nhất) chỉ có 1 nét, là chữ số 1.'),
('N5', 'Kanji nào có nghĩa là số "chín"?', '七', '八', '九', '十', 'C', '九 (cửu) nghĩa là "chín", kun''yomi ここの-つ.'),
('N5', 'Kanji 今 có kun''yomi là gì?', 'いま', 'とし', 'な', 'もと', 'A', '今 (kim) nghĩa là "bây giờ", kun''yomi いま.');
