--liquibase formatted sql
--changeset architect:7

ALTER TABLE seats ADD COLUMN IF NOT EXISTS booking_id UUID;

CREATE INDEX IF NOT EXISTS idx_seats_booking_id ON seats(booking_id);