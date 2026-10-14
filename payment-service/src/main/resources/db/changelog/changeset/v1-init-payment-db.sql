--liquibase formatted sql
--changeset korshuk:1
CREATE TABLE IF NOT EXISTS payments
(
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id     UUID           NOT NULL UNIQUE,
    user_id        UUID           NOT NULL,
    amount         DECIMAL(10, 2) NOT NULL,
    status         VARCHAR(50)    NOT NULL,
    failure_reason VARCHAR(255),
    created_at     TIMESTAMP      DEFAULT CURRENT_TIMESTAMP
    );

CREATE INDEX IF NOT EXISTS idx_payment_booking ON payments (booking_id);