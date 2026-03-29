-- ============================================================================
-- VALIDATION PLAN DATA - PERIODIC REVALIDATION PLANNING PHASE
-- ============================================================================
-- Migration: V2.28.1
-- Purpose: Create validation_plan_data table for PERIODIC_REVALIDATION procedure type
--          Stores plan details, mapping status, acceptance criteria, deviation procedures
--          Supports two QA approval paths: electronic signature + scanned document
-- Compliance: GMP Annex 15 §10 - Documented validation plan before measurement phase
--            FDA 21 CFR Part 11 - Electronic signatures with 2-person co-signature
-- ============================================================================

-- ============================================================================
-- TABLE: validation_plan_data
-- ============================================================================
-- Stores the planning phase data for PERIODIC_REVALIDATION procedure
-- One row per PERIODIC_REVALIDATION draft = one validation plan
-- Linked to validation_drafts via plan_data_id (1:1 relationship)
-- ============================================================================

CREATE TABLE validation_plan_data (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,

    -- ===== PLAN DETAILS (Step 3 of wizard) =====
    revalidation_reason LONGTEXT NULL
        COMMENT 'Uzasadnienie podjęcia rewalidacji okreowej',

    previous_validation_date DATE NULL
        COMMENT 'Data poprzedniej walidacji tego urządzenia',

    previous_validation_number VARCHAR(100) NULL
        COMMENT 'Numer sprawozdania z poprzedniej walidacji',

    -- ===== MAPPING STATUS (Step 4 - auto-filled from DB) =====
    mapping_check_date DATE NULL
        COMMENT 'Data sprawdzenia historii mapowań',

    mapping_status VARCHAR(20) NULL
        COMMENT 'Status mapowania: CURRENT (< 730 dni) | OVERDUE (>= 730 dni) | NEVER',

    mapping_overdue_acknowledged BOOLEAN DEFAULT FALSE
        COMMENT 'Potwierdzenie świadomej kontynuacji pomimo overdue mapowania',

    -- ===== LOAD STATE & ACCEPTANCE CRITERIA (Steps 5-6) =====
    -- Device load state for this revalidation
    plan_device_load_state VARCHAR(50) NULL
        COMMENT 'Stan obciążenia urządzenia (EMPTY, HALF_LOAD, FULL_LOAD)',

    -- Nominal temperature (from RecorderPosition or user input)
    plan_nominal_temp DOUBLE NULL
        COMMENT 'Temperatura nominalna dla tego testu (°C)',

    -- Acceptance criteria temperature range
    plan_acceptance_temp_min DOUBLE NULL
        COMMENT 'Minimalna temperatura akceptacji (°C)',

    plan_acceptance_temp_max DOUBLE NULL
        COMMENT 'Maksymalna temperatura akceptacji (°C)',

    -- MKT max threshold
    plan_mkt_max_temp DOUBLE NULL
        COMMENT 'Maksymalny MKT dopuszczony (°C)',

    -- Uniformity delta max
    plan_uniformity_delta_max DOUBLE NULL
        COMMENT 'Maksymalny dryft jednorodności (ΔT max, °C)',

    -- Drift per recorder max
    plan_drift_max_temp DOUBLE NULL
        COMMENT 'Maksymalny dryft na rejestrator (°C)',

    -- ===== DEVIATION PROCEDURES (Step 7) =====
    plan_deviation_critical_text LONGTEXT NULL
        COMMENT 'CAPA dla odchyleń krytycznych',

    plan_deviation_major_text LONGTEXT NULL
        COMMENT 'CAPA dla odchyleń poważnych',

    plan_deviation_minor_text LONGTEXT NULL
        COMMENT 'CAPA dla odchyleń mniejszych',

    -- ===== TECHNIK SIGNATURE ON PLAN (Step 8) =====
    plan_technik_signed_at DATETIME(6) NULL
        COMMENT 'Data/czas podpisu technologa na planie',

    plan_technik_username VARCHAR(50) NULL
        COMMENT 'Użytkownik (login) technologa, który podpisał plan',

    plan_technik_full_name VARCHAR(200) NULL
        COMMENT 'Imię i nazwisko technologa na podpisie',

    -- ===== QA APPROVAL SIGNATURE (electronic path) =====
    plan_qa_signed_at DATETIME(6) NULL
        COMMENT 'Data/czas podpisu QA na planie (ścieżka elektroniczna)',

    plan_qa_username VARCHAR(50) NULL
        COMMENT 'Użytkownik (login) QA, który zatwierdził plan',

    plan_qa_full_name VARCHAR(200) NULL
        COMMENT 'Imię i nazwisko QA na podpisie',

    -- ===== QA APPROVAL VIA SCANNED DOCUMENT (alternative path) =====
    plan_qa_scanned_document_path VARCHAR(500) NULL
        COMMENT 'Ścieżka do zeskanowanego podpisanego dokumentu (alternatywa dla e-podpisu)',

    plan_qa_scanned_uploaded_at DATETIME(6) NULL
        COMMENT 'Data/czas wgrania skanu',

    plan_qa_scanned_uploaded_by VARCHAR(50) NULL
        COMMENT 'Użytkownik, który wgrał skan',

    -- ===== REJECTION AUDIT TRAIL (if plan rejected) =====
    plan_rejection_reason LONGTEXT NULL
        COMMENT 'Powód odrzucenia planu przez QA',

    plan_rejected_at DATETIME(6) NULL
        COMMENT 'Data/czas odrzucenia planu',

    plan_rejected_by VARCHAR(50) NULL
        COMMENT 'Użytkownik QA, który odrzucił plan',

    rejection_attempt_count INT DEFAULT 0
        COMMENT 'Liczba prób odrzucenia (dla audytu)',

    -- ===== GENERATED PLAN PDF =====
    plan_pdf_path VARCHAR(500) NULL
        COMMENT 'Ścieżka do wygenerowanego planu PDF (podpisanego)',

    plan_document_number VARCHAR(100) NULL
        COMMENT 'Numer dokumentu przydzielony planowi (np: VP-2024-001)',

    plan_pdf_generated_at DATETIME(6) NULL
        COMMENT 'Data/czas generowania PDF planu',

    -- ===== TIMESTAMPS =====
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT 'Data utworzenia rekordu planu',

    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        COMMENT 'Data ostatniej aktualizacji rekordu planu',

    -- ===== INDEXES =====
    INDEX idx_plan_data_technik_username (plan_technik_username),
    INDEX idx_plan_data_qa_username (plan_qa_username),
    INDEX idx_plan_data_mapping_status (mapping_status),
    INDEX idx_plan_data_created_at (created_at),
    INDEX idx_plan_data_document_number (plan_document_number)

)
ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Plan walidacji dla rewalidacji okresowej (GMP Annex 15 §10) - dane planowania przed pomiarami';

-- ============================================================================
-- STEP 2: Add FK from validation_drafts to validation_plan_data
-- ============================================================================
ALTER TABLE validation_drafts
  ADD COLUMN plan_data_id BIGINT UNIQUE NULL
    COMMENT 'Odniesienie do planu walidacji (dla PERIODIC_REVALIDATION tylko)';

-- Add foreign key constraint
-- Jeden draft może mieć jeden plan, plan jest opcjonalny dla OQ/PQ/MAPPING
ALTER TABLE validation_drafts
  ADD CONSTRAINT fk_draft_plan_data
    FOREIGN KEY (plan_data_id) REFERENCES validation_plan_data(id)
      ON DELETE SET NULL;

-- ============================================================================
-- VERIFICATION
-- ============================================================================
SELECT CONCAT(
    'V2.28.1: Created validation_plan_data table with ',
    (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_NAME = 'validation_plan_data'),
    ' columns. Added plan_data_id FK to validation_drafts. At ',
    NOW()
) AS migration_status;
