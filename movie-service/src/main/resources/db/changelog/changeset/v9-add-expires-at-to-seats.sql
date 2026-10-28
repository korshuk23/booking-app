--liquibase formatted sql
--changeset korshuk:9

ALTER TABLE seats ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;
CREATE INDEX IF NOT EXISTS idx_seats_expiry ON seats (expires_at) WHERE status = 'RESERVED';