-- ============================================================================
-- V2.15.2 — Tabela C: Compliance (czas w zakresie, przekroczenia)
-- Dodaje kolumny Tabeli C do validation_summary_stats.
-- ============================================================================

ALTER TABLE validation_summary_stats
    -- C.1: Łączny czas w zakresie (suma z wszystkich serii)
    ADD COLUMN total_time_in_range_minutes      BIGINT  COMMENT 'C.1 Σ s.totalTimeInRangeMinutes [min]',

    -- C.2: Łączny czas poza zakresem
    ADD COLUMN total_time_out_of_range_minutes  BIGINT  COMMENT 'C.2 Σ s.totalTimeOutOfRangeMinutes [min]',

    -- C.3: Globalny wskaźnik zgodności [%]
    ADD COLUMN global_compliance_percentage     DOUBLE  COMMENT 'C.3 timeIn/(timeIn+timeOut)×100 [%]',

    -- C.4: Łączna liczba przekroczeń zakresu
    ADD COLUMN total_violations                 INT     COMMENT 'C.4 Σ s.violationCount',

    -- C.5: Najdłuższe pojedyncze przekroczenie (worst-case excursion)
    ADD COLUMN max_violation_duration_minutes   BIGINT  COMMENT 'C.5 max{ s.maxViolationDurationMinutes } [min]',
    ADD COLUMN max_violation_series_id          BIGINT  COMMENT 'ID serii z najdłuższym przekroczeniem',

    -- C.6: Liczba serii z przynajmniej jednym przekroczeniem
    ADD COLUMN series_with_violations_count     INT     COMMENT 'C.6 count{ s | s.violationCount > 0 }',

    -- C.7: Liczba serii w pełni zgodnych (zero przekroczeń)
    ADD COLUMN series_fully_compliant_count     INT     COMMENT 'C.7 count{ s | s.violationCount == 0 }';
