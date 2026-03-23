-- =============================================================================
-- V36: Tabela G — Deviation Events & Analyses
-- Wykrywanie naruszeń temperaturowych i dokumentacja CAPA
-- =============================================================================

CREATE TABLE deviation_events (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    validation_id          BIGINT       NOT NULL,
    measurement_series_id  BIGINT       NOT NULL,
    start_time             DATETIME     NOT NULL,
    end_time               DATETIME     NOT NULL,
    duration_minutes       BIGINT       NOT NULL,
    peak_temperature       DOUBLE       NOT NULL,
    violated_limit         DOUBLE       NOT NULL,
    violation_type         VARCHAR(20)  NOT NULL,
    severity               VARCHAR(20)  NOT NULL,

    CONSTRAINT fk_dev_event_validation
        FOREIGN KEY (validation_id) REFERENCES validations(id),
    CONSTRAINT fk_dev_event_series
        FOREIGN KEY (measurement_series_id) REFERENCES measurement_series(id)
);

CREATE INDEX idx_dev_event_validation ON deviation_events(validation_id);
CREATE INDEX idx_dev_event_severity   ON deviation_events(validation_id, severity);

CREATE TABLE deviation_analyses (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    deviation_event_id   BIGINT       NOT NULL UNIQUE,
    root_cause           TEXT,
    product_impact       TEXT,
    corrective_action    TEXT,
    analyzed_by          VARCHAR(100),
    analyzed_at          DATETIME,

    CONSTRAINT fk_dev_analysis_event
        FOREIGN KEY (deviation_event_id) REFERENCES deviation_events(id)
            ON DELETE CASCADE
);
