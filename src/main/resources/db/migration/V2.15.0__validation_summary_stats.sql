-- ============================================================================
-- V2.15.0 — Statystyki zbiorcze walidacji (ValidationSummaryStats)
-- Tabela relacja 1:1 z validations.
-- Tabela A: statystyki temperatury globalne (coldspot, hotspot, avg ważona,
--           odchylenie standardowe pooled, CV%, niepewność rozszerzona, percentyle).
-- Tabela E (metadane): liczba serii, czasy trwania, interwał dominujący.
-- ============================================================================

CREATE TABLE validation_summary_stats
(
    -- Klucz główny
    id                           BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- Relacja 1:1 z walidacją
    validation_id                BIGINT        NOT NULL,

    -- =========================================================================
    -- TABELA A — Statystyki temperatury (globalne)
    -- =========================================================================

    -- A.1: min { s.minTemperature } dla serii siatki (coldspot)
    global_min_temp              DOUBLE,

    -- A.2: max { s.maxTemperature } dla serii siatki (hotspot)
    global_max_temp              DOUBLE,

    -- A.3: Σ(s.avg * s.n) / Σ(s.n)  — ważona liczbą pomiarów
    overall_avg_temp             DOUBLE,

    -- A.4: sqrt(pooled variance z tożsamości Steinera dla połączonych grup)
    global_std_dev               DOUBLE,

    -- A.5: (global_std_dev / |overall_avg_temp|) * 100
    global_cv_percentage         DOUBLE,

    -- A.6: Seria siatki z najwyższą max temperaturą
    hotspot_temp                 DOUBLE,
    hotspot_series_id            BIGINT,

    -- A.7: Seria siatki z najniższą min temperaturą
    coldspot_temp                DOUBLE,
    coldspot_series_id           BIGINT,

    -- A.8: max { s.expandedUncertainty } — konserwatywne (k=2, 95% ufności, GUM)
    global_expanded_uncertainty  DOUBLE,

    -- A.9: min { s.percentile5 } / max { s.percentile95 } — przybliżenie konserwatywne
    global_percentile5           DOUBLE,
    global_percentile95          DOUBLE,

    -- =========================================================================
    -- METADANE WALIDACJI
    -- =========================================================================

    total_series_count           INT,
    grid_series_count            INT,
    reference_series_count       INT,
    total_measurement_count      BIGINT,
    validation_start_time        DATETIME,
    validation_end_time          DATETIME,
    total_duration_minutes       BIGINT,

    -- Moda z { s.measurementIntervalMinutes }
    dominant_interval_minutes    INT,

    -- Timestamp ostatniego obliczenia
    calculated_at                DATETIME      NOT NULL,

    -- Klucz obcy
    CONSTRAINT fk_vss_validation
        FOREIGN KEY (validation_id) REFERENCES validations (id)
            ON DELETE CASCADE,

    -- Unikalność (1:1)
    CONSTRAINT uq_vss_validation_id UNIQUE (validation_id)

) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Zbiorcze statystyki walidacji: Tabela A (temperatura) + metadane. Relacja 1:1 z validations.';

-- Indeks pomocniczy — wyszukiwanie po validation_id (pokryty przez UNIQUE, ale explicite)
CREATE INDEX idx_vss_validation_id ON validation_summary_stats (validation_id);
