--changeset korshuk:1
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    identifier VARCHAR(255) UNIQUE NOT NULL,
    otp_code VARCHAR(6),                     -- ДОБАВЛЕНО: для проверки кода
    otp_expiry TIMESTAMP,                    -- ДОБАВЛЕНО: чтобы код протухал
    role VARCHAR(50) DEFAULT 'USER',
    is_blocked BOOLEAN DEFAULT FALSE,        -- ДОБАВЛЕНО: на всякий случай
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);