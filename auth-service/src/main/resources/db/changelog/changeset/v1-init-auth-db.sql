--liquibase formatted sql
--changeset korshuk:3
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    identifier VARCHAR(255) UNIQUE NOT NULL,
    role VARCHAR(50) DEFAULT 'USER',
    is_blocked BOOLEAN DEFAULT FALSE,
    telegram_id VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);