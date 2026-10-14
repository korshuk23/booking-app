--changeset korshuk:2
INSERT INTO bookings (id, user_id, movie_id, session_id, status, price)
VALUES (gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), 'PENDING', 15.50),
       (gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), 'PENDING', 18.50)
ON CONFLICT DO NOTHING;