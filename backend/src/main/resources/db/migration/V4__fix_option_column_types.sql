-- Hibernate map Java String -> VARCHAR theo mặc định; đổi CHAR(1) -> VARCHAR(1)
-- để khớp với entity, tránh Schema-validation error khi ddl-auto=validate.
-- (Không sửa V1 vì Flyway migration đã áp dụng là bất biến.)

ALTER TABLE exam_questions ALTER COLUMN correct_option TYPE VARCHAR(1);
ALTER TABLE user_exam_answers ALTER COLUMN selected_option TYPE VARCHAR(1);
