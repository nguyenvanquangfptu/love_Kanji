-- Buổi làm đề JLPT chỉ lấy câu của một nguồn (vd. IMPORTED - các đề tự soạn) nếu người học chọn; null = mọi câu đã
-- duyệt. Lưu ở buổi thi để phần sau lấy câu từ đúng nguồn như phần đầu.
ALTER TABLE exam_sittings ADD COLUMN question_source VARCHAR(10);
ALTER TABLE exam_sittings ADD CONSTRAINT ck_exam_sittings_question_source
    CHECK (question_source IN ('MANUAL', 'GENERATED', 'AI', 'IMPORTED'));
