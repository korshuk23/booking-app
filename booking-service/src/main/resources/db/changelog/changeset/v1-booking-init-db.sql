--changeset korshuk:1
CREATE TABLE IF NOT EXISTS bookings
(
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL,
    movie_id   UUID        NOT NULL,
    session_id UUID      NOT NULL,
    /*Поправить на seat_id*/
    seat_id     UUID      NOT NULL,
    status     VARCHAR(50) NOT NULL,
    price      DECIMAL(10, 2),
    created_at TIMESTAMP        DEFAULT CURRENT_TIMESTAMP
);