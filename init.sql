SELECT 'CREATE DATABASE auth_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'auth_db')\gexec
SELECT 'CREATE DATABASE booking_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'booking_db')\gexec
SELECT 'CREATE DATABASE movie_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'movie_db')\gexec
SELECT 'CREATE DATABASE keycloak_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'keycloak_db')\gexec