-- Các cột trạng thái, phân loại và cấp độ chỉ nhận đúng các giá trị của enum Java tương ứng (model/*.java).
-- Trước đây cột VARCHAR nhận mọi chuỗi: một lỗi gõ ở service là câu lặng lẽ ra hoặc vào đề.
-- Tên ràng buộc theo mẫu ck_<bảng>_<cột>; StatusCheckConstraintsTest đối chiếu danh sách với enum.

-- Cấp độ JLPT (JlptLevel).
ALTER TABLE kanji ADD CONSTRAINT ck_kanji_jlpt_level CHECK (jlpt_level IN ('N5', 'N4', 'N3', 'N2', 'N1'));
ALTER TABLE exam_questions ADD CONSTRAINT ck_exam_questions_jlpt_level CHECK (jlpt_level IN ('N5', 'N4', 'N3', 'N2', 'N1'));
ALTER TABLE exam_passages ADD CONSTRAINT ck_exam_passages_jlpt_level CHECK (jlpt_level IN ('N5', 'N4', 'N3', 'N2', 'N1'));
ALTER TABLE grammar_points ADD CONSTRAINT ck_grammar_points_jlpt_level CHECK (jlpt_level IN ('N5', 'N4', 'N3', 'N2', 'N1'));
ALTER TABLE user_exam_attempts ADD CONSTRAINT ck_user_exam_attempts_jlpt_level CHECK (jlpt_level IN ('N5', 'N4', 'N3', 'N2', 'N1'));
ALTER TABLE exam_sittings ADD CONSTRAINT ck_exam_sittings_jlpt_level CHECK (jlpt_level IN ('N5', 'N4', 'N3', 'N2', 'N1'));
ALTER TABLE user_learning_profiles ADD CONSTRAINT ck_user_learning_profiles_target_level CHECK (target_level IN ('N5', 'N4', 'N3', 'N2', 'N1'));

-- Ngân hàng câu thi.
ALTER TABLE exam_questions ADD CONSTRAINT ck_exam_questions_status CHECK (status IN ('DRAFT', 'APPROVED', 'REJECTED', 'RETIRED'));
ALTER TABLE exam_questions ADD CONSTRAINT ck_exam_questions_source CHECK (source IN ('MANUAL', 'GENERATED', 'AI'));
ALTER TABLE exam_questions ADD CONSTRAINT ck_exam_questions_flag CHECK (flag IN ('WRONG_ANSWER', 'AMBIGUOUS', 'ABOVE_LEVEL', 'REPORTED', 'STATS'));
ALTER TABLE exam_questions ADD CONSTRAINT ck_exam_questions_question_type CHECK (question_type IN ('KANJI_READING', 'ORTHOGRAPHY', 'CONTEXT', 'PARAPHRASE', 'USAGE', 'GRAMMAR_FORM', 'SENTENCE_ORDER', 'TEXT_GRAMMAR'));
ALTER TABLE exam_questions ADD CONSTRAINT ck_exam_questions_skill CHECK (skill IN ('KANJI_TO_READING', 'READING_TO_KANJI', 'MEANING'));
ALTER TABLE exam_passages ADD CONSTRAINT ck_exam_passages_status CHECK (status IN ('DRAFT', 'APPROVED', 'REJECTED', 'RETIRED'));
ALTER TABLE exam_passages ADD CONSTRAINT ck_exam_passages_source CHECK (source IN ('MANUAL', 'GENERATED', 'AI'));
ALTER TABLE exam_passages ADD CONSTRAINT ck_exam_passages_flag CHECK (flag IN ('WRONG_ANSWER', 'AMBIGUOUS', 'ABOVE_LEVEL', 'REPORTED', 'STATS'));

-- Lượt thi, buổi thi.
ALTER TABLE user_exam_attempts ADD CONSTRAINT ck_user_exam_attempts_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'TIMEOUT'));
ALTER TABLE user_exam_attempts ADD CONSTRAINT ck_user_exam_attempts_section CHECK (section IN ('VOCABULARY', 'GRAMMAR'));
ALTER TABLE exam_sittings ADD CONSTRAINT ck_exam_sittings_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'ABANDONED'));
-- Các phần đã chọn, cách nhau bởi dấu phẩy (ExamSection).
ALTER TABLE exam_sittings ADD CONSTRAINT ck_exam_sittings_sections CHECK (sections ~ '^(VOCABULARY|GRAMMAR)(,(VOCABULARY|GRAMMAR))*$');

-- Báo lỗi câu hỏi.
ALTER TABLE exam_question_reports ADD CONSTRAINT ck_exam_question_reports_reason CHECK (reason IN ('WRONG_ANSWER', 'AMBIGUOUS', 'UNCLEAR', 'OTHER'));
ALTER TABLE exam_question_reports ADD CONSTRAINT ck_exam_question_reports_status CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED'));

-- Lịch sử ôn tập, cách xếp lịch.
ALTER TABLE review_logs ADD CONSTRAINT ck_review_logs_source CHECK (source IN ('FLASHCARD', 'QUIZ', 'EXAM'));
ALTER TABLE review_logs ADD CONSTRAINT ck_review_logs_direction CHECK (direction IN ('KANJI_TO_READING', 'READING_TO_KANJI', 'MEANING'));
ALTER TABLE review_logs ADD CONSTRAINT ck_review_logs_state_before CHECK (state_before IN ('NEW', 'REVIEW', 'RELEARNING'));
ALTER TABLE user_learning_profiles ADD CONSTRAINT ck_user_learning_profiles_scheduler CHECK (scheduler IN ('SM2', 'FSRS'));

-- Quyền tài khoản (gán ở server, không có enum Java).
ALTER TABLE users ADD CONSTRAINT ck_users_role CHECK (role IN ('ROLE_USER', 'ROLE_ADMIN'));
