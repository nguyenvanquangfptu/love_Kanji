-- Đề tự soạn nhập từ file (POST /api/v1/admin/exam-questions/import): nguồn IMPORTED, và mã của câu trong đề gốc
-- (vd. N3-05/NP/15, đoạn văn N3-05/NP/19-23) để tìm lại đúng câu khi cần sửa và để nhập lại một đề không tạo câu trùng.
ALTER TABLE exam_questions DROP CONSTRAINT ck_exam_questions_source;
ALTER TABLE exam_questions ADD CONSTRAINT ck_exam_questions_source CHECK (source IN ('MANUAL', 'GENERATED', 'AI', 'IMPORTED'));
ALTER TABLE exam_passages DROP CONSTRAINT ck_exam_passages_source;
ALTER TABLE exam_passages ADD CONSTRAINT ck_exam_passages_source CHECK (source IN ('MANUAL', 'GENERATED', 'AI', 'IMPORTED'));

ALTER TABLE exam_questions ADD COLUMN source_ref VARCHAR(60);
ALTER TABLE exam_passages ADD COLUMN source_ref VARCHAR(60);
CREATE UNIQUE INDEX ux_exam_questions_source_ref ON exam_questions (source_ref) WHERE source_ref IS NOT NULL;
CREATE UNIQUE INDEX ux_exam_passages_source_ref ON exam_passages (source_ref) WHERE source_ref IS NOT NULL;
