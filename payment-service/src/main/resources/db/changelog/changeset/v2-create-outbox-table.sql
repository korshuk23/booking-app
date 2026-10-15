--liquibase formatted sql
--changeset korshuk:2
CREATE TABLE IF NOT EXISTS outbox_events
(
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        TEXT         NOT NULL,
    status         VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at        TIMESTAMP
    );

CREATE INDEX IF NOT EXISTS idx_outbox_pending ON outbox_events (status, created_at);