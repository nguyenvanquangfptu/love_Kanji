# Benchmark: `idx_user_next_review` — API `GET /api/v1/srs/daily-cards`

## Bối cảnh

Composite index `idx_user_next_review (user_id, next_review_at)` trên bảng `user_kanji_srs`
được thiết kế để phục vụ trực tiếp query của API lấy danh sách thẻ cần ôn tập trong ngày.
Để chứng minh index thực sự hiệu quả trên **dữ liệu lớn** (không phải vài chục dòng demo),
đã seed dữ liệu synthetic bằng [`backend/scripts/benchmark_seed.sql`](../scripts/benchmark_seed.sql)
rồi đo `EXPLAIN ANALYZE` trên PostgreSQL 15 chạy trong Docker (`postgres:15-alpine`).

## Quy mô dữ liệu

| Thành phần | Số lượng |
|---|---|
| User giả (`bench_user_*`) | 5,000 |
| Kanji có sẵn | 50 |
| Dòng `user_kanji_srs` sinh ra (cross join, `next_review_at` rải ngẫu nhiên ±30 ngày) | **250,002** |

## Câu query được đo (đúng câu dùng trong `SrsService.getDailyCards`)

```sql
SELECT *
FROM user_kanji_srs
WHERE user_id = :userId
  AND next_review_at <= NOW()
ORDER BY next_review_at ASC
LIMIT 20;
```

## Kết quả `EXPLAIN ANALYZE` thật

```
Limit  (cost=8.71..48.92 rows=20 width=54) (actual time=0.025..0.028 rows=20 loops=1)
  InitPlan 1 (returns $0)
    ->  Index Scan using users_username_key on users  (cost=0.28..8.29 rows=1 width=8) (actual time=0.011..0.011 rows=1 loops=1)
          Index Cond: ((username)::text = 'bench_user_2500'::text)
  ->  Index Scan using idx_user_next_review on user_kanji_srs  (cost=0.42..52.69 rows=26 width=54) (actual time=0.024..0.026 rows=20 loops=1)
        Index Cond: ((user_id = $0) AND (next_review_at <= now()))
Planning Time: 0.302 ms
Execution Time: 0.470 ms
```

## Nhận xét

- Query planner chọn **Index Scan using `idx_user_next_review`** — đúng như thiết kế, không
  rơi vào Seq Scan dù bảng có 250,002 dòng.
- Cả điều kiện lọc (`user_id = ...`) lẫn điều kiện range + sắp xếp (`next_review_at <= now()`,
  `ORDER BY next_review_at`) đều được phục vụ bởi cùng một composite index, không cần sort
  riêng (không có node `Sort` trong plan).
- **Execution Time: 0.470ms** — thấp hơn mục tiêu $< 30\text{ms}$ khoảng 60 lần, trên tập dữ
  liệu 250K dòng trải đều nhiều user.

## Cách tái tạo

```bash
docker exec -i kanji_postgres psql -U kanji_user -d kanji_mastery_db < backend/scripts/benchmark_seed.sql
```

Dọn dữ liệu benchmark sau khi đo xong (tránh phình DB dev thật):

```sql
DELETE FROM user_kanji_srs WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'bench_user_%');
DELETE FROM users WHERE username LIKE 'bench_user_%';
```
