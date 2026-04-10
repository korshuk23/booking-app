--changeset korshuk:2
INSERT INTO bookings (user_id, movie_id, session_id, status, price)
VALUES (1, 1, 101, 'PENDING', 15.50),
       (2, 1, 101, 'PENDING', 18.50)
ON CONFLICT DO NOTHING;