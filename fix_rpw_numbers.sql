-- Skrypt naprawczy dla błędnie zinkrementowanych numerów RPW
-- Data: 2026-02-19
-- Opis: Resetuje numery RPW które zostały błędnie zinkrementowane

USE validation_system;

-- Pokaż obecny stan
SELECT 
    vpn.id,
    vpn.year,
    vpn.plan_number,
    cd.inventory_number AS device,
    cd.name AS device_name
FROM validation_plan_numbers vpn
JOIN cooling_devices cd ON vpn.cooling_device_id = cd.id
ORDER BY vpn.cooling_device_id, vpn.year;

-- Dla każdego urządzenia, zostaw tylko pierwszy rekord dla danego roku
-- i ustaw plan_number na podstawie kolejności urządzeń

-- PRZYKŁAD NAPRAWY (dostosuj cooling_device_id do swojego urządzenia):
-- UPDATE validation_plan_numbers 
-- SET plan_number = 1 
-- WHERE cooling_device_id = 5 AND year = 2026;

-- Jeśli chcesz usunąć wszystkie duplikaty i zacząć od nowa:
-- DELETE FROM validation_plan_numbers WHERE year = 2026 AND plan_number > 1;

-- Pokaż końcowy stan
SELECT 
    vpn.id,
    vpn.year,
    vpn.plan_number,
    cd.inventory_number AS device,
    cd.name AS device_name
FROM validation_plan_numbers vpn
JOIN cooling_devices cd ON vpn.cooling_device_id = cd.id
ORDER BY vpn.cooling_device_id, vpn.year;
