-- ============================================================================
-- VALIDATION WIZARD SCHEMA - OQ/PQ/MAPPING PROCEDURES
-- ============================================================================
-- Migration: V2.24.0
-- Purpose: Create tables for multi-step validation wizard with procedure-specific
--          acceptance criteria, OQ test results, and PQ checklist items
-- Compliance: GMP Annex 11, FDA 21 CFR Part 11, wizard persistent state
-- ============================================================================

-- ============================================================================
-- MAIN WIZARD TABLE: validation_drafts
-- ============================================================================
-- Stores the persistent state of a validation wizard session
-- One row per wizard = one validation being created step-by-step
-- Linked to cooling_device in step 2, measurement_series in step 6
-- Finalized to Validation entity in step 9
-- ============================================================================

CREATE TABLE validation_drafts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- WHO: Created by user (for authorization)
    created_by VARCHAR(255) NOT NULL COMMENT 'Username of wizard creator',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- WHICH PROCEDURE: OQ, PQ, or MAPPING
    procedure_type ENUM('OQ', 'PQ', 'MAPPING') NOT NULL COMMENT 'Typ procedury: OQ, PQ, lub Mapowanie',

    -- STEP TRACKING
    current_step INT NOT NULL DEFAULT 1 COMMENT 'Bieżący krok wizarda (1-9)',

    -- STEP LOCK: Once step 3 is saved (custom criteria), steps 2-3 are locked
    -- stepLockFrom = NULL means no lock; = 3 means steps 2-3 are locked from navigation back
    step_lock_from INT NULL COMMENT 'Od którego kroku zablokowana nawigacja wstecz (NULL=brak blokady)',

    -- STATUS TRACKING
    status ENUM('IN_PROGRESS', 'COMPLETED', 'ABANDONED') NOT NULL DEFAULT 'IN_PROGRESS' COMMENT 'Stan: IN_PROGRESS, COMPLETED, ABANDONED',

    -- DEVICE SELECTION (Step 2)
    cooling_device_id BIGINT NULL COMMENT 'Urządzenie chłodzące (wybrane w kroku 2)',

    -- MEASUREMENT SERIES SELECTION (Step 6)
    -- JSON array of Long IDs: [1, 2, 5, 10, ...] → requires custom converter
    selected_series_ids LONGTEXT NULL COMMENT 'JSON array of selected measurement_series IDs: [1, 2, 5, ...]',

    -- DEVICE LOAD STATE (Step 4)
    device_load_state VARCHAR(50) NULL COMMENT 'Stan obciążenia urządzenia (EMPTY, HALF_LOAD, FULL_LOAD)',

    -- RECORDER POSITION (Step 4)
    recorder_position VARCHAR(100) NULL COMMENT 'Pozycja czujnika (np: TOP_LEFT_FRONT)',

    -- FINALIZATION: Link to created Validation (Step 9, after signing)
    completed_validation_id BIGINT NULL COMMENT 'Walidacja utworzona po finalizacji kroku 9',

    -- FOREIGN KEY: cooling_device (nullable until step 2)
    CONSTRAINT fk_validation_drafts_cooling_device
        FOREIGN KEY (cooling_device_id) REFERENCES cooling_devices(id),

    -- FOREIGN KEY: completed_validation (nullable until step 9)
    CONSTRAINT fk_validation_drafts_completed_validation
        FOREIGN KEY (completed_validation_id) REFERENCES validations(id),

    -- CONSTRAINTS: Check step is 1-9
    CONSTRAINT chk_validation_drafts_step
        CHECK (current_step >= 1 AND current_step <= 9),

    -- CONSTRAINT: Check step_lock_from is 2-3 or NULL
    CONSTRAINT chk_validation_drafts_step_lock
        CHECK (step_lock_from IS NULL OR (step_lock_from >= 2 AND step_lock_from <= 3)),

    -- INDEX: For finding active drafts by user
    INDEX idx_validation_drafts_created_by (created_by),
    INDEX idx_validation_drafts_status (status),
    INDEX idx_validation_drafts_procedure_type (procedure_type),
    INDEX idx_validation_drafts_cooling_device_id (cooling_device_id),
    INDEX idx_validation_drafts_completed_validation_id (completed_validation_id)
);

-- ============================================================================
-- CUSTOM ACCEPTANCE CRITERIA (Step 3)
-- ============================================================================
-- Many-to-one: Each draft can have multiple custom criteria rows
-- Standard criteria from GMP are hardcoded in UI; custom ones are stored here
-- e.g.: AVG_TEMP > 5°C, MKT <= 20°C, etc.
-- ============================================================================

CREATE TABLE custom_acceptance_criteria (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    validation_draft_id BIGINT NOT NULL COMMENT 'Powiązany szkic walidacji',

    -- FIELD: Which metric to evaluate (MIN_TEMP, MAX_TEMP, AVG_TEMP, MKT, STD_DEV, etc.)
    field_name VARCHAR(100) NOT NULL COMMENT 'Pole do oceny: MIN_TEMP, MAX_TEMP, AVG_TEMP, MKT, STD_DEV, ...',

    -- OPERATOR: GT, LT, GTE, LTE, EQ
    operator VARCHAR(10) NOT NULL COMMENT 'Operator: GT, LT, GTE, LTE, EQ',

    -- LIMIT VALUE: The threshold (e.g. 20.0 for MKT <= 20.0°C)
    limit_value DECIMAL(10,4) NOT NULL COMMENT 'Wartość limitu (np. 20.0)',

    -- UNIT: °C, %, min, count, etc.
    unit VARCHAR(20) NULL COMMENT 'Jednostka (°C, %, min, count, ...)',

    -- ORDERING & METADATA
    display_order INT NOT NULL DEFAULT 0 COMMENT 'Kolejność wyświetlania',
    is_standard BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Czy to standardowe kryterium GMP',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_custom_acceptance_criteria_draft
        FOREIGN KEY (validation_draft_id) REFERENCES validation_drafts(id) ON DELETE CASCADE,

    INDEX idx_custom_acceptance_criteria_draft (validation_draft_id)
);

-- ============================================================================
-- OQ TEST RESULTS (Step 5 for OQ procedure only)
-- ============================================================================
-- One-to-one semantics: Each draft (if OQ) has at most one OqTestResult row
-- Stores results of power failure, alarm, door tests
-- ============================================================================

CREATE TABLE oq_test_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    validation_draft_id BIGINT NOT NULL UNIQUE COMMENT 'Powiązany szkic OQ (1:1)',

    -- Power Failure Test (Awaria zasilania)
    power_failure_test_passed BOOLEAN NULL COMMENT 'Wynik testu awarii zasilania',
    power_failure_notes VARCHAR(1000) NULL COMMENT 'Notatki do testu awarii zasilania',

    -- Alarm Verification Test (Weryfikacja alarmu)
    alarm_test_passed BOOLEAN NULL COMMENT 'Wynik testu weryfikacji alarmu',
    alarm_test_notes VARCHAR(1000) NULL COMMENT 'Notatki do testu alarmu',

    -- Door Lock Test (Test drzwi)
    door_test_passed BOOLEAN NULL COMMENT 'Wynik testu drzwi',
    door_test_notes VARCHAR(1000) NULL COMMENT 'Notatki do testu drzwi',

    created_by VARCHAR(255) NOT NULL COMMENT 'Użytkownik, który wypełnił wyniki',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_oq_test_results_draft
        FOREIGN KEY (validation_draft_id) REFERENCES validation_drafts(id) ON DELETE CASCADE,

    INDEX idx_oq_test_results_draft (validation_draft_id)
);

-- ============================================================================
-- PQ CHECKLIST ITEMS (Step 5 for PQ procedure only)
-- ============================================================================
-- Many-to-one: Each draft (if PQ) has 10+ PQ checklist rows (PQ-01..PQ-10+)
-- Stores individual checklist items with pass/fail and comments
-- ============================================================================

CREATE TABLE pq_checklist_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    validation_draft_id BIGINT NOT NULL COMMENT 'Powiązany szkic PQ',

    -- ITEM CODE & DESCRIPTION
    item_code VARCHAR(10) NOT NULL COMMENT 'Kod pozycji listy (PQ-01, PQ-02, ...)',
    item_description VARCHAR(500) NOT NULL COMMENT 'Opis pozycji listy',

    -- RESULT
    passed BOOLEAN NULL COMMENT 'Wynik: true=spełnione, false=niespełnione, NULL=nie oceniane',
    comment VARCHAR(1000) NULL COMMENT 'Komentarz do pozycji listy',

    -- ORDERING
    display_order INT NOT NULL DEFAULT 0 COMMENT 'Kolejność wyświetlania',

    -- METADATA
    created_by VARCHAR(255) NULL COMMENT 'Użytkownik, który wypełnił pozycję',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_pq_checklist_items_draft
        FOREIGN KEY (validation_draft_id) REFERENCES validation_drafts(id) ON DELETE CASCADE,

    -- CONSTRAINT: item_code must be PQ-XX format
    CONSTRAINT chk_pq_checklist_item_code
        CHECK (item_code REGEXP '^PQ-[0-9]{2}$'),

    INDEX idx_pq_checklist_items_draft (validation_draft_id),
    INDEX idx_pq_checklist_items_code (item_code)
);

-- ============================================================================
-- AUDIT TRAIL FOR GMP COMPLIANCE
-- ============================================================================
-- Envers will automatically create:
--   validation_drafts_AUD
--   custom_acceptance_criteria_AUD
--   oq_test_results_AUD
--   pq_checklist_items_AUD
-- ============================================================================

-- ============================================================================
-- COMMENTS FOR GMP AUDITORS
-- ============================================================================
-- 1. Validation drafts are persistent wizard sessions, not finalized validations
-- 2. Each draft tracks current step (1-9) and wizard flow state
-- 3. Step lock prevents navigation back after custom criteria (step 3) are locked in
-- 4. Measurement series selection in step 6 is JSON array (custom converter handles serialization)
-- 5. OQ-specific: power failure, alarm, door tests (1 result row per draft)
-- 6. PQ-specific: 10-item checklist with pass/fail tracking (N rows per draft)
-- 7. All modifications tracked via Envers audit trail per entity
-- 8. Draft → Validation happens in step 9 (finalization) via WizardFinalizationService
-- 9. Drafts abandoned or completed are archived with status field
-- ============================================================================
