-- Migracja v2.9.4: Dodanie kolumny validation_plan_number
-- Data: 2026-02-19
-- Opis: Kolumna przechowująca numer RPW w formacie "1/2026"

USE validation_system;

-- Sprawdź czy kolumna już istnieje
SET @column_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'validation_system'
    AND TABLE_NAME = 'validations'
    AND COLUMN_NAME = 'validation_plan_number'
);

-- Dodaj kolumnę jeśli nie istnieje
SET @sql = IF(@column_exists = 0,
    'ALTER TABLE validations ADD COLUMN validation_plan_number VARCHAR(20) DEFAULT NULL AFTER id',
    'SELECT ''Kolumna validation_plan_number już istnieje'' AS info'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Pokaż status
SELECT 
    CASE 
        WHEN @column_exists = 0 THEN '✅ Dodano kolumnę validation_plan_number'
        ELSE '⚠️ Kolumna validation_plan_number już istniała'
    END AS Status;

-- Pokaż strukturę tabeli
DESCRIBE validations;
