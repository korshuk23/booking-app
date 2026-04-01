--liquibase formatted sql
--changeset korshuk:2
INSERT INTO movies (title, description, genre) VALUES
    ('Интерстеллар', 'Путешествие сквозь пространство и время', 'Sci-Fi'),
    ('Начало', 'Погружение в мир осознанных сновидений', 'Action'),
    ('Джентльмены', 'Криминальная комедия Гая Ричи', 'Comedy');