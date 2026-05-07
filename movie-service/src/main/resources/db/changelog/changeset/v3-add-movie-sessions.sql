--liquibase formatted sql
CREATE TABLE movie_sessions
(
    id              UUID PRIMARY KEY,
    movie_id        UUID      NOT NULL,
    start_time      TIMESTAMP NOT NULL,
    total_seats     INTEGER   NOT NULL,
    available_seats INTEGER   NOT NULL,
    CONSTRAINT fk_movie FOREIGN KEY (movie_id) REFERENCES movies (id)
);

-- 2. Таблица-предохранитель для идемпотентности
CREATE TABLE processed_bookings
(
    booking_id   UUID PRIMARY KEY,
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);