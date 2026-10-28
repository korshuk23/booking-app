--liquibase formatted sql
--changeset korshuk:8

ALTER TABLE seats ALTER COLUMN reserved_at DROP DEFAULT;
UPDATE seats SET reserved_at = NULL WHERE status = 'FREE';

CREATE INDEX IF NOT EXISTS idx_seats_reserved_at ON seats (reserved_at) WHERE status = 'RESERVED';