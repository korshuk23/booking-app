--liquibase formatted sql
--changeset korshuk:4
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;