-- V2.18.0 - Dodanie pól dla śledzenia wymuszonej zmiany hasła
-- Autor: Claude Code Assistant
-- Data: 2026-03-05
-- Opis: Dodaje pola must_change_password i password_changed_at do tabeli users
--       dla implementacji wymuszonej zmiany hasła przy pierwszym logowaniu

-- Dodanie kolumn do tabeli users
ALTER TABLE users
ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE
COMMENT 'Czy użytkownik musi zmienić hasło przy następnym logowaniu',

ADD COLUMN password_changed_at DATETIME
COMMENT 'Data i czas ostatniej zmiany hasła przez użytkownika';

-- Dodanie indeksów dla wydajności
CREATE INDEX idx_users_must_change_password ON users(must_change_password);
CREATE INDEX idx_users_password_changed_at ON users(password_changed_at);

-- Ustaw password_changed_at dla istniejących użytkowników na czas utworzenia konta
-- (zakładamy że hasło było "zmienione" przy tworzeniu konta)
UPDATE users
SET password_changed_at = created_date
WHERE password_changed_at IS NULL;

-- Komentarz do tabeli
ALTER TABLE users COMMENT = 'Użytkownicy systemu z polityką wymuszania zmiany hasła przy pierwszym logowaniu';