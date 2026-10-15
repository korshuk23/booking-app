--liquibase formatted sql
--changeset korshuk:4

CREATE TABLE seats (
                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       session_id UUID NOT NULL,
                       row_number INT NOT NULL,
                       seat_number INT NOT NULL,
                       status VARCHAR(20) NOT NULL DEFAULT 'FREE',
                       version BIGINT NOT NULL DEFAULT 0,
                       reserved_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                       CONSTRAINT fk_seat_session FOREIGN KEY (session_id) REFERENCES movie_sessions(id),
                       CONSTRAINT uq_session_row_seat UNIQUE (session_id, row_number, seat_number)
);


-- Индекс для быстрого поиска свободных мест на сеанс
CREATE INDEX idx_seats_session_status ON seats(session_id, status);