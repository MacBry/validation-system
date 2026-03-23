-- V2.17.0 - Dodanie stanu załadowania urządzenia podczas walidacji
-- Autor: Claude Code Assistant
-- Data: 2026-03-04
-- Opis: Dodaje pole device_load_state do tabeli validations
--       dla określenia czy urządzenie było pełne/puste podczas walidacji

-- Dodanie kolumny device_load_state do tabeli validations
ALTER TABLE validations
ADD COLUMN device_load_state ENUM('FULL', 'EMPTY', 'PARTIALLY_LOADED')
COMMENT 'Stan załadowania urządzenia: FULL (pełne), EMPTY (puste), PARTIALLY_LOADED (częściowo załadowane)';

-- Dodanie indeksu na device_load_state dla wydajności zapytań
CREATE INDEX idx_validations_device_load_state ON validations(device_load_state);

-- Komentarz do tabeli z wyjaśnieniem pola
ALTER TABLE validations COMMENT = 'Walidacje urządzeń chłodniczych ze stanem załadowania: FULL (symuluje rzeczywiste warunki), EMPTY (warunki bez obciążenia), PARTIALLY_LOADED (warunki pośrednie)';