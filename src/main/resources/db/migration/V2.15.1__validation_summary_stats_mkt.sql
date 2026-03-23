-- ============================================================================
-- V2.15.1 — Tabela B: MKT (Mean Kinetic Temperature) w validation_summary_stats
-- Dodaje kolumny MKT do istniejącej tabeli validation_summary_stats.
-- ============================================================================

ALTER TABLE validation_summary_stats
    -- B.1: Globalny MKT z tożsamości Arrheniusa: MKT_g = ΔH/R / -ln(Σ(n_s·e^(-ΔH/R/MKTsk))/N) - 273.15
    ADD COLUMN global_mkt                       DOUBLE          COMMENT 'B.1 Globalny MKT z agregacji Arrheniusa wszystkich serii siatki [°C]',

    -- B.2: ΔH/R [K] użyta do obliczeń — do odtworzenia w dokumentacji
    ADD COLUMN mkt_delta_h_r                    DOUBLE          COMMENT 'B.2 ΔH/R [K] = activationEnergy[J/mol] / R[J/(mol·K)]',

    -- B.3: Najwyższy MKT (worst case)
    ADD COLUMN mkt_worst_value                  DOUBLE          COMMENT 'B.3 max{ s.mktTemperature } — najgorszy przypadek [°C]',
    ADD COLUMN mkt_worst_series_id              BIGINT          COMMENT 'ID serii z najwyższym MKT',

    -- B.4: Najniższy MKT siatki (best case)
    ADD COLUMN mkt_best_value                   DOUBLE          COMMENT 'B.4 min{ s.mktTemperature | isReference=false } [°C]',
    ADD COLUMN mkt_best_series_id               BIGINT          COMMENT 'ID serii z najniższym MKT',

    -- B.5: MKT rejestratora referencyjnego (otoczenie)
    ADD COLUMN mkt_reference_value              DOUBLE          COMMENT 'B.5 MKT serii referencyjnej (otoczenie) [°C]',
    ADD COLUMN mkt_reference_series_id          BIGINT          COMMENT 'ID serii referencyjnej',

    -- B.6: Różnica MKT wewnętrz - referencja (wartość ujemna = komora chłodzi)
    ADD COLUMN mkt_delta_internal_vs_reference  DOUBLE          COMMENT 'B.6 globalMkt − mktReferenceValue [°C]';
