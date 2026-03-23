-- ============================================================================
-- GUM UNCERTAINTY BUDGET SCHEMA - GMP COMPLIANCE
-- ============================================================================
-- Migration: V2.23.0
-- Purpose: Create uncertainty budget tables and add resolution field to recorders
-- Compliance: GUM (JCGM 100:2008), ISO/IEC 17025, FDA 21 CFR Part 11
-- ============================================================================

-- Create uncertainty_budgets table
CREATE TABLE uncertainty_budgets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- Type A uncertainty (statistical)
    statistical_uncertainty DECIMAL(10,6) NOT NULL COMMENT 'Niepewność statystyczna (Typ A): σ/√n [°C]',

    -- Type B uncertainties (systematic)
    calibration_uncertainty DECIMAL(10,6) NOT NULL COMMENT 'Niepewność kalibracji (Typ B): z certyfikatu [°C]',
    resolution_uncertainty DECIMAL(10,6) NOT NULL COMMENT 'Niepewność rozdzielczości (Typ B): res/(2√3) [°C]',
    systematic_uncertainty DECIMAL(10,6) NOT NULL COMMENT 'Niepewność systematyczna (Typ B): bias/√3 [°C]',
    stability_uncertainty DECIMAL(10,6) NOT NULL COMMENT 'Niepewność stabilności (Typ B): drift/√3 [°C]',

    -- Spatial uncertainty (only for validation level)
    spatial_uncertainty DECIMAL(10,6) NULL COMMENT 'Niepewność przestrzenna (Typ B): σ_spatial/√n_pos [°C]',

    -- Combined and expanded uncertainties
    combined_uncertainty DECIMAL(10,6) NOT NULL COMMENT 'Niepewność łączna: √(Σu_i²) [°C]',
    expanded_uncertainty DECIMAL(10,6) NOT NULL COMMENT 'Niepewność rozszerzona: k×u_c [°C]',

    -- Coverage factor and confidence level
    coverage_factor DECIMAL(4,2) NOT NULL DEFAULT 2.00 COMMENT 'Współczynnik pokrycia k (typowo 2.0)',
    confidence_level DECIMAL(5,2) NOT NULL DEFAULT 95.45 COMMENT 'Poziom ufności [%] (typowo 95.45%)',
    degrees_of_freedom INT NULL COMMENT 'Efektywne stopnie swobody ν_eff',

    -- Budget metadata
    budget_type ENUM('SERIES', 'VALIDATION') NOT NULL COMMENT 'Typ budżetu: SERIES lub VALIDATION',
    calculation_notes VARCHAR(1000) NULL COMMENT 'Notatki o kontekście obliczenia',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
    CONSTRAINT chk_uncertainties_positive CHECK (
        statistical_uncertainty >= 0 AND
        calibration_uncertainty >= 0 AND
        resolution_uncertainty >= 0 AND
        systematic_uncertainty >= 0 AND
        stability_uncertainty >= 0 AND
        (spatial_uncertainty IS NULL OR spatial_uncertainty >= 0) AND
        combined_uncertainty > 0 AND
        expanded_uncertainty > 0
    ),

    CONSTRAINT chk_coverage_factor CHECK (coverage_factor > 0 AND coverage_factor <= 10),
    CONSTRAINT chk_confidence_level CHECK (confidence_level > 0 AND confidence_level <= 100)
);

-- Add resolution field to thermo_recorders
ALTER TABLE thermo_recorders
ADD COLUMN resolution DECIMAL(4,3) NULL COMMENT 'Rozdzielczość cyfrowa [°C] (np. 0.100 dla TESTO)';

-- Add uncertainty_budget_id to measurement_series
ALTER TABLE measurement_series
ADD COLUMN uncertainty_budget_id BIGINT NULL COMMENT 'Budżet niepewności dla tej serii';

-- Add uncertainty_budget_id to validation_summary_stats (for validation-level budget)
ALTER TABLE validation_summary_stats
ADD COLUMN validation_uncertainty_budget_id BIGINT NULL COMMENT 'Budżet niepewności na poziomie walidacji';

-- Create foreign key constraints
ALTER TABLE measurement_series
ADD CONSTRAINT fk_measurement_series_uncertainty_budget
FOREIGN KEY (uncertainty_budget_id) REFERENCES uncertainty_budgets(id);

ALTER TABLE validation_summary_stats
ADD CONSTRAINT fk_validation_stats_uncertainty_budget
FOREIGN KEY (validation_uncertainty_budget_id) REFERENCES uncertainty_budgets(id);

-- Create indexes for performance
CREATE INDEX idx_uncertainty_budgets_type ON uncertainty_budgets(budget_type);
CREATE INDEX idx_uncertainty_budgets_created_at ON uncertainty_budgets(created_at);
CREATE INDEX idx_measurement_series_uncertainty_budget ON measurement_series(uncertainty_budget_id);
CREATE INDEX idx_validation_stats_uncertainty_budget ON validation_summary_stats(validation_uncertainty_budget_id);

-- Set default resolution values based on recorder model
UPDATE thermo_recorders
SET resolution = 0.100
WHERE model LIKE '%TESTO%' OR model LIKE '%175%';

UPDATE thermo_recorders
SET resolution = 0.010
WHERE model LIKE '%PRECISION%' OR model LIKE '%PT100%' OR model LIKE '%HIGH%';

-- Set default resolution for unmatched models
UPDATE thermo_recorders
SET resolution = 0.100
WHERE resolution IS NULL;

-- ============================================================================
-- AUDIT TRAIL FOR GMP COMPLIANCE
-- ============================================================================

-- Create audit table for uncertainty_budgets (manual - Envers will handle this automatically)
-- This is here for documentation purposes

-- ============================================================================
-- COMMENTS FOR GMP AUDITORS
-- ============================================================================
-- 1. Uncertainty budget follows GUM (JCGM 100:2008) methodology
-- 2. Type A: statistical uncertainty from measurement repeatability
-- 3. Type B: systematic uncertainties from calibration, resolution, stability
-- 4. RSS combination: u_c = √(u_A² + u_B1² + u_B2² + ... + u_Bn²)
-- 5. Expanded uncertainty: U = k × u_c (typically k=2 for 95% confidence)
-- 6. Each measurement series has individual uncertainty budget
-- 7. Validation has aggregated uncertainty budget including spatial component
-- 8. All data traceable to calibration certificates via CalibrationPoint
-- ============================================================================