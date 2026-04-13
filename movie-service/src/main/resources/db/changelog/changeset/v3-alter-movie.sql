--liquibase formatted sql
--changeset korshuk:4
ALTER TABLE movies
    ADD COLUMN price DOUBLE PRECISION DEFAULT 0.0,
    ADD COLUMN is_available BOOLEAN DEFAULT TRUE;