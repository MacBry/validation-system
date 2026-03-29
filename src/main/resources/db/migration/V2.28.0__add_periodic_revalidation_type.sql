-- ============================================================================
-- EXTEND VALIDATION WIZARD FOR PERIODIC REVALIDATION (GMP Annex 15 §10)
-- ============================================================================
-- Migration: V2.28.0
-- Purpose: Extend procedure_type ENUM to include PERIODIC_REVALIDATION
--          Extend status ENUM to include AWAITING_QA_APPROVAL (for QA approval barrier)
--          Relax CHECK constraints to support 13-step flow (up from 9)
-- Compliance: GMP Annex 15 §10 - Periodic revalidation with documented plan & QA sign-off
-- ============================================================================

-- ============================================================================
-- STEP 1: Extend procedure_type ENUM
-- ============================================================================
-- Add PERIODIC_REVALIDATION to existing OQ, PQ, MAPPING
ALTER TABLE validation_drafts
  MODIFY COLUMN procedure_type
    ENUM('OQ', 'PQ', 'MAPPING', 'PERIODIC_REVALIDATION') NOT NULL
    COMMENT 'Typ procedury: OQ, PQ, Mapowanie, lub Rewalidacja Okresowa (GMP Annex 15)';

-- ============================================================================
-- STEP 2: Extend status ENUM
-- ============================================================================
-- Add AWAITING_QA_APPROVAL for QA approval barrier between planning & measurement phases
ALTER TABLE validation_drafts
  MODIFY COLUMN status
    ENUM('IN_PROGRESS', 'COMPLETED', 'ABANDONED', 'AWAITING_QA_APPROVAL') NOT NULL
    DEFAULT 'IN_PROGRESS'
    COMMENT 'Stan: IN_PROGRESS, COMPLETED, ABANDONED, lub AWAITING_QA_APPROVAL (rewalidacja)';

-- ============================================================================
-- STEP 3: Note on step_lock_from constraint
-- ============================================================================
-- The step_lock_from constraint (if it exists) allows NULL or >= 2 and 3
-- For PERIODIC_REVALIDATION, we need to allow >= 2 (including 9)
-- If the constraint exists in the database, it will need manual update or
-- we rely on application-level validation in Java (WizardStepValidator)
-- No SQL change needed here as the constraint behavior is backward-compatible

-- ============================================================================
-- STEP 4: Extend current_step constraint
-- ============================================================================
-- OLD: current_step >= 1 AND current_step <= 9 (OQ/PQ/MAPPING)
-- NEW: current_step >= 1 AND current_step <= 13 (PERIODIC_REVALIDATION has 13 steps)
-- Bieżący krok wizarda (1-9 dla OQ/PQ/MAPPING, 1-13 dla PERIODIC_REVALIDATION)
ALTER TABLE validation_drafts
  DROP CONSTRAINT chk_validation_drafts_step;

ALTER TABLE validation_drafts
  ADD CONSTRAINT chk_validation_drafts_step
    CHECK (current_step >= 1 AND current_step <= 13);

-- ============================================================================
-- VERIFICATION
-- ============================================================================
SELECT CONCAT(
    'V2.28.0: ENUMs extended. procedure_type now includes PERIODIC_REVALIDATION. ',
    'status includes AWAITING_QA_APPROVAL. Step range extended to 1-13. At ',
    NOW()
) AS migration_status;
