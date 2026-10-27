-- Tìm từ không cần gõ dấu: "hoc sinh" khớp Hán Việt HỌC SINH, "bac si" khớp nghĩa "Bác sĩ" (unaccent bỏ dấu, kể cả
-- Đ → D). unaccent là extension "trusted" từ PostgreSQL 13 nên chủ database tự bật được. Kho từ chỉ vài nghìn dòng nên
-- chưa cần index trên unaccent(...).
CREATE EXTENSION IF NOT EXISTS unaccent;
