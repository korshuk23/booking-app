--liquibase formatted sql
--changeset korshuk:3

ALTER TABLE payments ADD COLUMN IF NOT EXISTS session_id UUID;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS seat_id UUID;