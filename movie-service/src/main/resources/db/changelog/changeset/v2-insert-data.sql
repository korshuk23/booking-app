--liquibase formatted sql
--changeset korshuk:2

INSERT INTO movies (id, title, description, release_date, genre, price, is_available)
VALUES
    (gen_random_uuid(), 'Интерстеллар', 'Путешествие сквозь пространство и время для спасения человечества.', '2014-11-07', 'Sci-Fi',15.00, TRUE),
    (gen_random_uuid(), 'Начало', 'Профессиональный вор крадет секреты из подсознания через сны.', '2010-07-16', 'Action',12.50, TRUE),
    (gen_random_uuid(), 'Дюна: Часть вторая', 'Пол Атрейдес объединяется с фрименами, чтобы отомстить заговорщикам.', '2024-03-01','Adventure', 20.00, TRUE),
    (gen_random_uuid(), 'Темный рыцарь', 'Бэтмен поднимает ставки в войне с криминалом в Готэме.', '2008-07-18', 'Drama',10.00, TRUE);