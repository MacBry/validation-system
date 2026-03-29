-- ============================================================================
-- ADD ROLE_QA FOR PERIODIC REVALIDATION PLAN APPROVAL
-- ============================================================================
-- Migration: V2.28.2
-- Purpose: Add new Spring Security role ROLE_QA for Quality Assurance users
--          QA users can review and approve validation plans for PERIODIC_REVALIDATION
-- Compliance: GMP Annex 15 §10 - QA approval required before measurement phase
--            FDA 21 CFR Part 11 - Two-person co-signature rule (technik + QA)
-- ============================================================================

-- ============================================================================
-- STEP 1: Insert ROLE_QA into roles table
-- ============================================================================
-- Using INSERT IGNORE to prevent errors if role already exists
INSERT IGNORE INTO roles (name, description) VALUES
  ('ROLE_QA', 'Quality Assurance - Plan reviewer & approver for PERIODIC_REVALIDATION');

-- ============================================================================
-- VERIFICATION
-- ============================================================================
SELECT CONCAT(
    'V2.28.2: Added ROLE_QA. Existing roles: ',
    (SELECT GROUP_CONCAT(CONCAT(name, ' (', IFNULL(description, 'N/A'), ')') SEPARATOR ', ')
     FROM roles ORDER BY name),
    '. At ',
    NOW()
) AS migration_status;
