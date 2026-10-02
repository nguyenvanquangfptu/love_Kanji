-- Câu thi kiểu đề JLPT (sinh từ kho từ vựng): câu ví dụ và phần được gạch chân trong câu (từ cần đọc, hoặc cách đọc
-- của từ cần viết). NULL với câu hỏi không có câu ví dụ.
ALTER TABLE exam_questions ADD COLUMN sentence TEXT;
ALTER TABLE exam_questions ADD COLUMN highlight VARCHAR(100);
