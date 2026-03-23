-- ============================================================================
-- V2.27.0 - Fix revinfo timestamp nullable for Envers support
-- ============================================================================
-- Purpose: Allow NULL values in timestamp column to support Hibernate Envers
-- Compliance: GMP Annex 11 - Audit trail integration with Hibernate Envers
-- Issue: "Field 'timestamp' doesn't have a default value" during Envers inserts
-- Solution: Make timestamp column nullable (Envers will populate it via @RevisionTimestamp)
-- ============================================================================

-- Ensure timestamp column is nullable (already done but confirmed here for idempotency)
ALTER TABLE revinfo MODIFY COLUMN timestamp BIGINT NULL DEFAULT NULL COMMENT 'Timestamp Unix (ms) - populated by Hibernate Envers';

-- Confirm the migration
SELECT CONCAT('revinfo.timestamp is now nullable to support Hibernate Envers at ', NOW()) AS migration_status;
