-- Ai viết câu ví dụ của từ: MANUAL (soạn tay, nhập theo bài) hay AI (Gemini). Câu thi sinh tự động từ câu do AI viết phải
-- vào hàng chờ duyệt (DRAFT), không vào đề ngay. Câu có từ trước migration này không rõ nguồn: để NULL, coi như soạn tay.
ALTER TABLE kanji ADD COLUMN example_sentence_source VARCHAR(10);
ALTER TABLE kanji ADD CONSTRAINT ck_kanji_example_sentence_source
    CHECK (example_sentence_source IN ('MANUAL', 'AI'));
