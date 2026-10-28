-- Tài khoản do quản trị viên cấp (khi tắt tự đăng ký) có mật khẩu tạm: người học phải đổi ở lần đăng nhập đầu.
-- Tài khoản đã có giữ nguyên (false).
ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
