--liquibase formatted sql
--changeset korshuk:5

CREATE INDEX IF NOT EXISTS idx_bookings_user_id ON bookings(user_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_active_booking_per_seat
    ON bookings(seat_id)
    WHERE status IN ('PENDING', 'SEATS_RESERVED', 'CONFIRMED');