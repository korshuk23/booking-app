--changeset korshuk:1
CREATE TABLE IF NOT EXISTS bookings (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    movie_id UUID NOT NULL,
    session_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL,
    price DECIMAL(10, 2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);