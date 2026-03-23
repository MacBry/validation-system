-- V2.16.0 - Dodanie klasyfikacji kubatury według PDA TR-64 i WHO
-- Autor: Claude Code Assistant
-- Data: 2026-03-04
-- Opis: Dodaje pola volume_m3 i volume_category do tabeli cooling_devices
--       zgodnie z farmaceutycznymi standardami walidacji

-- Dodanie kolumn do tabeli cooling_devices
ALTER TABLE cooling_devices
ADD COLUMN volume_m3 DECIMAL(8,3) COMMENT 'Objętość urządzenia w metrach sześciennych (PDA TR-64)',
ADD COLUMN volume_category ENUM('SMALL', 'MEDIUM', 'LARGE') COMMENT 'Klasyfikacja kubatury: SMALL (≤2m³), MEDIUM (2-20m³), LARGE (>20m³)';

-- Dodanie indeksu na volume_category dla wydajności zapytań
CREATE INDEX idx_cooling_devices_volume_category ON cooling_devices(volume_category);

-- Komentarz do tabeli z wyjaśnieniem klasyfikacji
ALTER TABLE cooling_devices COMMENT = 'Urządzenia chłodnicze z klasyfikacją kubatury wg PDA TR-64: SMALL (≤2m³, min 9 punktów), MEDIUM (2-20m³, min 15 punktów), LARGE (>20m³, min 27 punktów)';