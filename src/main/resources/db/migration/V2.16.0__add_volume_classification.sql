-- V2.16.0 - Dodanie klasyfikacji kubatury według PDA TR-64 i WHO
-- Autor: Claude Code Assistant
-- Data: 2026-03-04
-- Opis: Dodaje pola volume_m3 i volume_category do tabeli cooling_devices
--       zgodnie z farmaceutycznymi standardami walidacji

-- Dodanie kolumn do tabeli cooling_devices (idempotent)
SET @dbname = DATABASE();
SET @tablename = 'cooling_devices';

-- Add volume_m3 if it doesn't exist
SET @columnname = 'volume_m3';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname
   AND TABLE_NAME = @tablename
   AND COLUMN_NAME = @columnname) = 0,
  CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @columnname, ' DECIMAL(8,3) COMMENT "Objętość urządzenia w metrach sześciennych (PDA TR-64)"'),
  'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add volume_category if it doesn't exist
SET @columnname2 = 'volume_category';
SET @preparedStatement2 = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname
   AND TABLE_NAME = @tablename
   AND COLUMN_NAME = @columnname2) = 0,
  CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @columnname2, ' ENUM("SMALL", "MEDIUM", "LARGE") COMMENT "Klasyfikacja kubatury: SMALL (≤2m³), MEDIUM (2-20m³), LARGE (>20m³)"'),
  'SELECT 1'
));
PREPARE stmt2 FROM @preparedStatement2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- Dodanie indeksu na volume_category dla wydajności zapytań (idempotent)
SET @indexname = 'idx_cooling_devices_volume_category';
SET @preparedStatement3 = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
   WHERE TABLE_SCHEMA = @dbname
   AND TABLE_NAME = @tablename
   AND INDEX_NAME = @indexname) = 0,
  CONCAT('CREATE INDEX ', @indexname, ' ON ', @tablename, '(volume_category)'),
  'SELECT 1'
));
PREPARE stmt3 FROM @preparedStatement3;
EXECUTE stmt3;
DEALLOCATE PREPARE stmt3;

-- Komentarz do tabeli z wyjaśnieniem klasyfikacji
ALTER TABLE cooling_devices COMMENT = 'Urządzenia chłodnicze z klasyfikacją kubatury wg PDA TR-64: SMALL (≤2m³, min 9 punktów), MEDIUM (2-20m³, min 15 punktów), LARGE (>20m³, min 27 punktów)';