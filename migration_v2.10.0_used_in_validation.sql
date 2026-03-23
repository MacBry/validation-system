-- Migracja v2.10.0: Dodanie kolumny used_in_validation
-- Data: 2026-02-19
-- Opis: Kolumna oznaczająca czy seria pomiarowa została już użyta w walidacji

USE validation_system;

-- Sprawdź czy kolumna już istnieje
SET @column_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'validation_system'
    AND TABLE_NAME = 'measurement_series'
    AND COLUMN_NAME = 'used_in_validation'
);

-- Dodaj kolumnę jeśli nie istnieje
SET @sql = IF(@column_exists = 0,
    'ALTER TABLE measurement_series ADD COLUMN used_in_validation BOOLEAN NOT NULL DEFAULT FALSE AFTER measurement_count',
    'SELECT ''Kolumna used_in_validation już istnieje'' AS info'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Pokaż status
SELECT 
    CASE 
        WHEN @column_exists = 0 THEN '✅ Dodano kolumnę used_in_validation'
        ELSE '⚠️ Kolumna used_in_validation już istniała'
    END AS Status;

-- OPCJONALNIE: Oznacz istniejące serie które są już w walidacjach jako użyte
-- To zapewni spójność danych jeśli masz już utworzone walidacje
UPDATE measurement_series ms
SET ms.used_in_validation = TRUE
WHERE ms.id IN (
    SELECT DISTINCT vms.measurement_series_id 
    FROM validation_measurement_series vms
);

-- Pokaż ile serii zostało oznaczonych jako użyte
SELECT 
    COUNT(*) as total_series,
    SUM(CASE WHEN used_in_validation = TRUE THEN 1 ELSE 0 END) as used_series,
    SUM(CASE WHEN used_in_validation = FALSE THEN 1 ELSE 0 END) as unused_series
FROM measurement_series;

-- Pokaż strukturę tabeli
DESCRIBE measurement_series;
