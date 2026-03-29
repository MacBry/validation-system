-- ============================================================================
-- FIX AUDIT TABLE ENUM FOR PERIODIC REVALIDATION (RETRY)
-- ============================================================================
-- Migration: V2.28.4
-- Root cause: V2.28.3 was recorded as applied but the column type in
--   validation_drafts_AUD was not actually updated. This migration
--   converts the ENUM columns to VARCHAR(50) in the audit table
--   which is the safest approach for Envers audit tables - they should
--   NOT have ENUM constraints since they need to accept any value
--   the main table sends, including future additions.
-- ============================================================================

-- ============================================================================
-- STEP 1: Convert procedure_type from ENUM to VARCHAR(50)
-- ============================================================================
-- This is the column causing "Data truncated for column 'procedure_type'"
-- because the ENUM only contains ('OQ','PQ','MAPPING') but Hibernate
-- tries to insert 'PERIODIC_REVALIDATION'.

ALTER TABLE `validation_drafts_AUD`
  MODIFY COLUMN `procedure_type` VARCHAR(50) NULL;

-- ============================================================================
-- STEP 2: Convert status from ENUM to VARCHAR(30)
-- ============================================================================
-- Future-proofing: 'AWAITING_QA_APPROVAL' must also be accepted.

ALTER TABLE `validation_drafts_AUD`
  MODIFY COLUMN `status` VARCHAR(30) NULL;

-- ============================================================================
-- STEP 3: Ensure plan_data_id column exists (may have been added by ddl-auto)
-- ============================================================================

SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND UPPER(TABLE_NAME) = 'VALIDATION_DRAFTS_AUD'
    AND COLUMN_NAME = 'plan_data_id'
);

SET @sql = IF(@col_exists = 0,
  'ALTER TABLE `validation_drafts_AUD` ADD COLUMN `plan_data_id` BIGINT NULL',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================================
-- VERIFICATION
-- ============================================================================
SELECT CONCAT(
    'V2.28.4 APPLIED: validation_drafts_AUD.procedure_type → VARCHAR(50), ',
    'status → VARCHAR(30). At ', NOW()
) AS migration_status;
