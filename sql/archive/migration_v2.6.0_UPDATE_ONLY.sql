-- Update script dla v2.6.0 (gdy kolumna już istnieje)
-- Użyj tego skryptu jeśli dostałeś błąd "Duplicate column name"

-- Wylicz średnią dla każdej walidacji
UPDATE validations v
SET average_device_temperature = (
    SELECT AVG(ms.avg_temperature)
    FROM validation_measurement_series vms
    JOIN measurement_series ms ON vms.measurement_series_id = ms.id
    WHERE vms.validation_id = v.id
)
WHERE EXISTS (
    SELECT 1 
    FROM validation_measurement_series vms 
    WHERE vms.validation_id = v.id
);

-- Sprawdź wynik
SELECT 
    v.id,
    v.validation_plan_number,
    v.average_device_temperature,
    COUNT(vms.measurement_series_id) as series_count,
    v.created_date
FROM validations v
LEFT JOIN validation_measurement_series vms ON v.id = vms.validation_id
GROUP BY v.id, v.validation_plan_number, v.average_device_temperature, v.created_date
ORDER BY v.id;
