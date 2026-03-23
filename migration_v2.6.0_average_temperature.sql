-- Migration v2.6.0: Dodanie pola average_device_temperature do tabeli validations
-- Data: 2026-02-18
-- Opis: Pole przechowuje średnią temperaturę urządzenia wyliczoną ze wszystkich serii pomiarowych

-- Sprawdź czy kolumna już istnieje i dodaj tylko jeśli nie ma
SET @column_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 'validations' 
    AND COLUMN_NAME = 'average_device_temperature'
);

SET @sql = IF(@column_exists = 0,
    'ALTER TABLE validations ADD COLUMN average_device_temperature DOUBLE NULL COMMENT ''Średnia temperatura w urządzeniu (wyliczona ze wszystkich serii)''',
    'SELECT ''Kolumna average_device_temperature już istnieje, pomijam'' AS Info'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Aktualizuj istniejące rekordy (tylko te które mają NULL)
-- Wylicz średnią dla każdej walidacji na podstawie serii pomiarowych
UPDATE validations v
SET average_device_temperature = (
    SELECT AVG(ms.avg_temperature)
    FROM validation_measurement_series vms
    JOIN measurement_series ms ON vms.measurement_series_id = ms.id
    WHERE vms.validation_id = v.id
)
WHERE average_device_temperature IS NULL
AND EXISTS (
    SELECT 1 
    FROM validation_measurement_series vms 
    WHERE vms.validation_id = v.id
);

-- Sprawdź wynik
SELECT 
    id,
    validation_plan_number,
    average_device_temperature,
    (SELECT COUNT(*) FROM validation_measurement_series vms WHERE vms.validation_id = validations.id) as series_count,
    created_date
FROM validations
ORDER BY id;
