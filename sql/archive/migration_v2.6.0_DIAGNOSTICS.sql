-- Diagnostyka v2.6.0 - sprawdź stan bazy danych

-- 1. Sprawdź czy kolumna istnieje
SELECT 
    COLUMN_NAME,
    DATA_TYPE,
    IS_NULLABLE,
    COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
AND TABLE_NAME = 'validations'
AND COLUMN_NAME = 'average_device_temperature';

-- 2. Pokaż wszystkie walidacje z serią i średnią
SELECT 
    v.id,
    v.validation_plan_number,
    v.average_device_temperature as 'Średnia w bazie',
    COUNT(vms.measurement_series_id) as 'Liczba serii',
    (SELECT AVG(ms.avg_temperature)
     FROM validation_measurement_series vms2
     JOIN measurement_series ms ON vms2.measurement_series_id = ms.id
     WHERE vms2.validation_id = v.id) as 'Średnia obliczona',
    v.created_date
FROM validations v
LEFT JOIN validation_measurement_series vms ON v.id = vms.validation_id
GROUP BY v.id, v.validation_plan_number, v.average_device_temperature, v.created_date
ORDER BY v.id;

-- 3. Pokaż walidacje gdzie średnia NIE PASUJE do obliczonej
SELECT 
    v.id,
    v.validation_plan_number,
    v.average_device_temperature as 'W bazie',
    (SELECT AVG(ms.avg_temperature)
     FROM validation_measurement_series vms2
     JOIN measurement_series ms ON vms2.measurement_series_id = ms.id
     WHERE vms2.validation_id = v.id) as 'Powinna być',
    ABS(v.average_device_temperature - 
        (SELECT AVG(ms.avg_temperature)
         FROM validation_measurement_series vms2
         JOIN measurement_series ms ON vms2.measurement_series_id = ms.id
         WHERE vms2.validation_id = v.id)) as 'Różnica'
FROM validations v
WHERE v.average_device_temperature IS NOT NULL
AND EXISTS (
    SELECT 1 FROM validation_measurement_series vms WHERE vms.validation_id = v.id
)
HAVING ABS(`W bazie` - `Powinna być`) > 0.01;

-- 4. Pokaż walidacje bez średniej (NULL)
SELECT 
    v.id,
    v.validation_plan_number,
    v.average_device_temperature,
    COUNT(vms.measurement_series_id) as series_count
FROM validations v
LEFT JOIN validation_measurement_series vms ON v.id = vms.validation_id
GROUP BY v.id, v.validation_plan_number, v.average_device_temperature
HAVING v.average_device_temperature IS NULL AND series_count > 0;
