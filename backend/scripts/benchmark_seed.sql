-- Script benchmark THỦ CÔNG - KHÔNG phải Flyway migration, không tự chạy khi khởi động app.
-- Mục đích: sinh dữ liệu quy mô lớn để đo EXPLAIN ANALYZE thật cho idx_user_next_review,
-- làm bằng chứng cho benchmark "< 30ms" nêu trong README, thay vì đo trên vài chục dòng demo.
--
-- Cách chạy:
--   docker exec -i kanji_postgres psql -U kanji_user -d kanji_mastery_db < backend/scripts/benchmark_seed.sql
--
-- Dọn dẹp sau khi đo xong (khuyến nghị, để không làm phình dữ liệu dev thật):
--   DELETE FROM user_kanji_srs WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'bench_user_%');
--   DELETE FROM users WHERE username LIKE 'bench_user_%';

-- 1. Sinh 5,000 user giả (mật khẩu hash giả, KHÔNG dùng để đăng nhập thật)
INSERT INTO users (username, email, password_hash, role, created_at)
SELECT
    'bench_user_' || gs,
    'bench_user_' || gs || '@example.com',
    '$2a$10$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ01',
    'ROLE_USER',
    NOW()
FROM generate_series(1, 5000) AS gs
ON CONFLICT (username) DO NOTHING;

-- 2. Cross join với 50 Kanji đã seed -> tối đa 250,000 dòng user_kanji_srs,
--    next_review_at rải ngẫu nhiên từ -30 đến +30 ngày để chỉ số thực sự phải làm việc
--    (không phải toàn bộ dữ liệu cùng 1 ngày).
INSERT INTO user_kanji_srs (user_id, kanji_id, repetition_count, easiness_factor, review_interval_days, next_review_at, last_reviewed_at)
SELECT
    u.id,
    k.id,
    floor(random() * 8)::int,
    round((1.3 + random() * 1.7)::numeric, 2),
    floor(random() * 30)::int,
    NOW() + make_interval(days => (floor(random() * 60) - 30)::int),
    NOW() - make_interval(days => floor(random() * 10)::int)
FROM users u
CROSS JOIN kanji k
WHERE u.username LIKE 'bench_user_%'
ON CONFLICT (user_id, kanji_id) DO NOTHING;

-- 3. Cập nhật thống kê cho query planner (quan trọng để EXPLAIN ANALYZE phản ánh đúng thực tế)
ANALYZE user_kanji_srs;

SELECT
    (SELECT count(*) FROM users WHERE username LIKE 'bench_user_%') AS synthetic_users,
    (SELECT count(*) FROM user_kanji_srs) AS total_srs_rows;

-- 4. Đo hiệu năng câu query thật dùng trong GET /api/v1/srs/daily-cards
--    (lấy 1 user bench bất kỳ để đo, tương đương load thật của 1 user)
EXPLAIN ANALYZE
SELECT *
FROM user_kanji_srs
WHERE user_id = (SELECT id FROM users WHERE username = 'bench_user_2500')
  AND next_review_at <= NOW()
ORDER BY next_review_at ASC
LIMIT 20;
