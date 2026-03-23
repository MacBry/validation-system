-- ============================================================================
-- AUDIT TRAIL HASH CHAIN INTEGRITY - GMP COMPLIANCE
-- ============================================================================
-- Migration: V2.22.0
-- Purpose: Add hash chain fields for tamper-evident audit logging
-- Compliance: GMP Annex 11 Section 9 (Audit Trail)
-- FDA 21 CFR Part 11 Section 11.10(e) (Audit Trail Protection)
-- ============================================================================

-- Add hash chain columns to audit_log table
ALTER TABLE audit_log
ADD COLUMN previous_hash VARCHAR(64) NULL COMMENT 'SHA-256 hash of previous audit record',
ADD COLUMN record_hash VARCHAR(64) NULL UNIQUE COMMENT 'SHA-256 hash of this audit record';

-- Create index for hash chain queries
CREATE INDEX idx_audit_log_record_hash ON audit_log(record_hash);

-- Create index for previous hash lookups
CREATE INDEX idx_audit_log_previous_hash ON audit_log(previous_hash);

-- Add comment to table describing GMP compliance
ALTER TABLE audit_log COMMENT = 'GMP Annex 11 compliant audit trail with hash chain integrity protection';

-- ============================================================================
-- NOTES FOR GMP AUDITORS:
-- ============================================================================
-- 1. Hash chain ensures tamper-evident logging per GMP Annex 11 Section 9
-- 2. SHA-256 provides cryptographic integrity verification
-- 3. previous_hash links to prior record creating unbreakable chain
-- 4. record_hash is unique constraint preventing duplicate/replayed records
-- 5. Any modification breaks the hash chain and is immediately detectable
-- 6. Verification method available: AuditService.verifyAuditTrailIntegrity()
-- ============================================================================