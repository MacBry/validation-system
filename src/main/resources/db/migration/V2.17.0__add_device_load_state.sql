-- V2.17.0 - Dodanie stanu załadowania urządzenia podczas walidacji
-- Autor: Claude Code Assistant
-- Data: 2026-03-04
-- Opis: Dodaje pole device_load_state do tabeli validations
--       dla określenia czy urządzenie było pełne/puste podczas walidacji

-- Dodanie kolumny device_load_state do tabeli validations (idempotent)
SET @dbname = DATABASE();
SET @tablename = 'validations';
SET @columnname = 'device_load_state';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname
   AND TABLE_NAME = @tablename
   AND COLUMN_NAME = @columnname) = 0,
  CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @columnname, ' ENUM("FULL", "EMPTY", "PARTIALLY_LOADED") COMMENT "Stan załadowania urządzenia: FULL (pełne), EMPTY (puste), PARTIALLY_LOADED (częściowo załadowane)"'),
  'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Dodanie indeksu na device_load_state dla wydajności zapytań (idempotent)
SET @indexname = 'idx_validations_device_load_state';
SET @preparedStatement2 = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
   WHERE TABLE_SCHEMA = @dbname
   AND TABLE_NAME = @tablename
   AND INDEX_NAME = @indexname) = 0,
  CONCAT('CREATE INDEX ', @indexname, ' ON ', @tablename, '(device_load_state)'),
  'SELECT 1'
));
PREPARE stmt2 FROM @preparedStatement2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- Komentarz do tabeli z wyjaśnieniem pola
ALTER TABLE validations COMMENT = 'Walidacje urządzeń chłodniczych ze stanem załadowania: FULL (symuluje rzeczywiste warunki), EMPTY (warunki bez obciążenia), PARTIALLY_LOADED (warunki pośrednie)';