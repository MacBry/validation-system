-- V2.18.0 - Dodanie pól dla śledzenia wymuszonej zmiany hasła
-- Autor: Claude Code Assistant
-- Data: 2026-03-05
-- Opis: Dodaje pola must_change_password i password_changed_at do tabeli users
--       dla implementacji wymuszonej zmiany hasła przy pierwszym logowaniu
--       NOTE: Columns created in V2.12.0 security schema - this migration is idempotent

-- Dodanie kolumn do tabeli users (idempotent - columns may already exist from V2.12.0)
SET @dbname = DATABASE();
SET @tablename = 'users';

-- Add must_change_password if it doesn't exist
SET @columnname = 'must_change_password';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname
   AND TABLE_NAME = @tablename
   AND COLUMN_NAME = @columnname) = 0,
  CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @columnname, ' BOOLEAN NOT NULL DEFAULT FALSE COMMENT "Czy użytkownik musi zmienić hasło przy następnym logowaniu"'),
  'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add password_changed_at if it doesn't exist
SET @columnname2 = 'password_changed_at';
SET @preparedStatement2 = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname
   AND TABLE_NAME = @tablename
   AND COLUMN_NAME = @columnname2) = 0,
  CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @columnname2, ' DATETIME COMMENT "Data i czas ostatniej zmiany hasła przez użytkownika"'),
  'SELECT 1'
));
PREPARE stmt2 FROM @preparedStatement2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- Dodanie indeksów dla wydajności (idempotent)
SET @indexname1 = 'idx_users_must_change_password';
SET @preparedStatement3 = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
   WHERE TABLE_SCHEMA = @dbname
   AND TABLE_NAME = @tablename
   AND INDEX_NAME = @indexname1) = 0,
  CONCAT('CREATE INDEX ', @indexname1, ' ON ', @tablename, '(must_change_password)'),
  'SELECT 1'
));
PREPARE stmt3 FROM @preparedStatement3;
EXECUTE stmt3;
DEALLOCATE PREPARE stmt3;

SET @indexname2 = 'idx_users_password_changed_at';
SET @preparedStatement4 = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
   WHERE TABLE_SCHEMA = @dbname
   AND TABLE_NAME = @tablename
   AND INDEX_NAME = @indexname2) = 0,
  CONCAT('CREATE INDEX ', @indexname2, ' ON ', @tablename, '(password_changed_at)'),
  'SELECT 1'
));
PREPARE stmt4 FROM @preparedStatement4;
EXECUTE stmt4;
DEALLOCATE PREPARE stmt4;

-- Ustaw password_changed_at dla istniejących użytkowników na czas utworzenia konta
-- (zakładamy że hasło było "zmienione" przy tworzeniu konta)
UPDATE users
SET password_changed_at = created_date
WHERE password_changed_at IS NULL;

-- Komentarz do tabeli
ALTER TABLE users COMMENT = 'Użytkownicy systemu z polityką wymuszania zmiany hasła przy pierwszym logowaniu';