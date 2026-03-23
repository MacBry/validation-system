-- FIX: Poprawka średnich temperatur urządzeń w walidacjach
-- Problem: Serie referencyjne (bez pozycji) zniekształcają średnią temperatury urządzenia
-- Rozwiązanie: Przelicz średnią tylko z serii mających recorder_position (wykluczając REF)

-- Wyświetl obecne błędne wartości
SELECT
    v.id as validation_id,
    v.average_device_temperature as current_avg,
    ROUND(AVG(ms.avg_temperature), 6) as correct_avg_with_ref,
    ROUND(AVG(CASE WHEN ms.recorder_position IS NOT NULL THEN ms.avg_temperature END), 6) as correct_avg_device_only
FROM validations v
JOIN validation_measurement_series vms ON v.id = vms.validation_id
JOIN measurement_series ms ON vms.measurement_series_id = ms.id
WHERE v.id >= 18  -- Walidacje z problemem
GROUP BY v.id, v.average_device_temperature
ORDER BY v.id;

-- Aktualizuj średnie temperatury dla wszystkich walidacji (wykluczając serie REF)
UPDATE validations v
SET average_device_temperature = (
    SELECT ROUND(AVG(ms.avg_temperature), 6)
    FROM validation_measurement_series vms
    JOIN measurement_series ms ON vms.measurement_series_id = ms.id
    WHERE vms.validation_id = v.id
      AND ms.avg_temperature IS NOT NULL
      AND ms.recorder_position IS NOT NULL  -- WYKLUCZAMY SERIE REF!
)
WHERE v.id IN (18, 19, 20, 21);  -- Tylko walidacje z błędnymi wartościami

-- Pokaż poprawione wartości
SELECT
    id,
    average_device_temperature as corrected_avg,
    created_date
FROM validations
WHERE id >= 17
ORDER BY id DESC;