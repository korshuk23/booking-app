--liquibase formatted sql
--changeset architect:6

-- 1. Добавляем сеанс на завтра для первого фильма (Интерстеллар)
INSERT INTO movie_sessions (id, movie_id, start_time, total_seats, available_seats)
SELECT
    'a0000000-0000-0000-0000-000000000001'::uuid,
    id,
    NOW() + INTERVAL '1 day',
    10,
    10
FROM movies
WHERE title = 'Интерстеллар'
    LIMIT 1
ON CONFLICT DO NOTHING;

-- 2. Добавляем 3 тестовых кресла в этот сеанс
INSERT INTO seats (id, session_id, row_number, seat_number, status, version)
VALUES
    ('b0000000-0000-0000-0000-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 1, 1, 'FREE', 0),
    ('b0000000-0000-0000-0000-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 1, 2, 'FREE', 0),
    ('b0000000-0000-0000-0000-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 1, 3, 'FREE', 0)
    ON CONFLICT DO NOTHING;