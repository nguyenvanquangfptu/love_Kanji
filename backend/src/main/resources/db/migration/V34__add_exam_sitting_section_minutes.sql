-- Thời gian người học tự đặt cho từng phần của buổi làm đề JLPT, vd. "VOCABULARY:40,GRAMMAR:30"; phần không có ở đây
-- (hoặc null cả cột) theo thời gian đề thật. Buổi thi có thời gian tự đặt không được tính vào bảng xếp hạng.
ALTER TABLE exam_sittings ADD COLUMN section_minutes VARCHAR(60);
