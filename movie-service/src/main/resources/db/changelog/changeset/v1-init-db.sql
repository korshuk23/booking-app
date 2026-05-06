--liquibase formatted sql
--changeset korshuk:1
CREATE TABLE IF NOT EXISTS movies
(
    id           UUID PRIMARY KEY,
    title        VARCHAR(255) NOT NULL,
    description  TEXT,
    release_date DATE,
    genre        VARCHAR(100),
    price        DECIMAL(10, 2)           DEFAULT 0.00,
    is_available BOOLEAN                  DEFAULT TRUE,
    created_at   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Индекс для быстрого поиска по жанрам
CREATE INDEX idx_movies_genre ON movies (genre);

-- Индекс для фильтрации доступных фильмов
CREATE INDEX idx_movies_availability ON movies (is_available);