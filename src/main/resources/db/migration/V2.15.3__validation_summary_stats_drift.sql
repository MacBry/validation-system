-- ============================================================================
-- V2.15.3 — Tabela D: Stabilność/Drift/Spike w validation_summary_stats
-- ============================================================================

ALTER TABLE validation_summary_stats
    -- D.1: Największy bezwzględny współczynnik trendu (worst case drift)
    ADD COLUMN max_abs_trend_coefficient   DOUBLE          COMMENT 'D.1 max|s.trendCoefficient| [°C/h]',
    ADD COLUMN max_trend_series_id         BIGINT          COMMENT 'ID serii z najsilniejszym driftem',

    -- D.2: Średnioważony współczynnik trendu
    ADD COLUMN avg_trend_coefficient       DOUBLE          COMMENT 'D.2 Σ(n_s × trend_s)/N [°C/h]',

    -- D.3: Łączna liczba spike-ów
    ADD COLUMN total_spike_count           INT             COMMENT 'D.3 Σ s.spikeCount',

    -- D.4: Liczba serii DRIFT lub MIXED
    ADD COLUMN series_with_drift_count     INT             COMMENT 'D.4 count{ s.classification IN (DRIFT,MIXED) }',

    -- D.5: Liczba serii STABLE
    ADD COLUMN series_stable_count         INT             COMMENT 'D.5 count{ s.classification = STABLE }',

    -- D.6: Dominująca klasyfikacja (moda)
    ADD COLUMN dominant_drift_classification VARCHAR(10)   COMMENT 'D.6 moda{ s.driftClassification }';
