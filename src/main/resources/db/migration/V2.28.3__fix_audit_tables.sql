-- ============================================================================
-- FIX AUDIT TABLES FOR PERIODIC REVALIDATION
-- ============================================================================
-- Migration: V2.28.3
-- Purpose: Update ENUMs in validation_drafts_AUD to match main table
--          Add plan_data_id column to audit table
--          Create validation_plan_data_AUD for auditing new plan entity
-- Compliance: GMP Annex 15 - Audit trail for all validation lifecycle changes
-- ============================================================================

-- ============================================================================
-- STEP 1: Update validation_drafts_AUD ENUMs
-- ============================================================================
-- Hibernate Envers (suffix=_AUD) auto-created this table with original ENUMs.
-- The procedure_type column is ENUM('OQ','PQ','MAPPING') — needs PERIODIC_REVALIDATION.
-- The status column is ENUM('IN_PROGRESS','COMPLETED','ABANDONED') — needs AWAITING_QA_APPROVAL.

ALTER TABLE `validation_drafts_AUD`
  MODIFY COLUMN `procedure_type`
    ENUM('OQ', 'PQ', 'MAPPING', 'PERIODIC_REVALIDATION') NULL;

ALTER TABLE `validation_drafts_AUD`
  MODIFY COLUMN `status`
    ENUM('IN_PROGRESS', 'COMPLETED', 'ABANDONED', 'AWAITING_QA_APPROVAL') NULL;

-- ============================================================================
-- STEP 2: Add plan_data_id to audit table (added by V2.28.1 to main table)
-- ============================================================================
-- ddl-auto=update may have already added this; use a procedure to be safe.

SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'validation_drafts_AUD'
    AND COLUMN_NAME = 'plan_data_id'
);

SET @sql = IF(@col_exists = 0,
  'ALTER TABLE `validation_drafts_AUD` ADD COLUMN `plan_data_id` BIGINT NULL',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================================
-- STEP 3: Create validation_plan_data_AUD
-- ============================================================================
CREATE TABLE IF NOT EXISTS `validation_plan_data_AUD` (
    `id`       BIGINT  NOT NULL,
    `REV`      INT     NOT NULL,
    `REVTYPE`  TINYINT NULL,

    `revalidation_reason`            LONGTEXT     NULL,
    `previous_validation_date`       DATE         NULL,
    `previous_validation_number`     VARCHAR(100) NULL,
    `mapping_check_date`             DATE         NULL,
    `mapping_status`                 VARCHAR(20)  NULL,
    `mapping_overdue_acknowledged`   BOOLEAN      NULL,
    `plan_device_load_state`         VARCHAR(50)  NULL,
    `plan_nominal_temp`              DOUBLE       NULL,
    `plan_acceptance_temp_min`       DOUBLE       NULL,
    `plan_acceptance_temp_max`       DOUBLE       NULL,
    `plan_mkt_max_temp`              DOUBLE       NULL,
    `plan_uniformity_delta_max`      DOUBLE       NULL,
    `plan_drift_max_temp`            DOUBLE       NULL,
    `plan_deviation_critical_text`   LONGTEXT     NULL,
    `plan_deviation_major_text`      LONGTEXT     NULL,
    `plan_deviation_minor_text`      LONGTEXT     NULL,
    `plan_technik_signed_at`         DATETIME(6)  NULL,
    `plan_technik_username`          VARCHAR(50)  NULL,
    `plan_technik_full_name`         VARCHAR(200) NULL,
    `plan_qa_signed_at`              DATETIME(6)  NULL,
    `plan_qa_username`               VARCHAR(50)  NULL,
    `plan_qa_full_name`              VARCHAR(200) NULL,
    `plan_qa_scanned_document_path`  VARCHAR(500) NULL,
    `plan_qa_scanned_uploaded_at`    DATETIME(6)  NULL,
    `plan_qa_scanned_uploaded_by`    VARCHAR(50)  NULL,
    `plan_rejection_reason`          LONGTEXT     NULL,
    `plan_rejected_at`               DATETIME(6)  NULL,
    `plan_rejected_by`               VARCHAR(50)  NULL,
    `rejection_attempt_count`        INT          NULL,
    `plan_pdf_path`                  VARCHAR(500) NULL,
    `plan_document_number`           VARCHAR(100) NULL,
    `plan_pdf_generated_at`          DATETIME(6)  NULL,
    `created_at`                     TIMESTAMP    NULL,
    `updated_at`                     TIMESTAMP    NULL,

    PRIMARY KEY (`id`, `REV`),
    CONSTRAINT `fk_validation_plan_data_AUD_rev`
        FOREIGN KEY (`REV`) REFERENCES `REVINFO`(`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Historia planów walidacji — Envers audit trail (GMP Annex 15)';

-- ============================================================================
-- VERIFICATION
-- ============================================================================
SELECT CONCAT(
    'V2.28.3: validation_drafts_AUD ENUMs extended. ',
    'validation_plan_data_AUD created. At ',
    NOW()
) AS migration_status;
