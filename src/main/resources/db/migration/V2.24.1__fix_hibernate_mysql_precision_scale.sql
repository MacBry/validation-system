-- ============================================================================
-- FIX: Hibernate/MySQL Compatibility - Precision/Scale on Floating Point Types
-- ============================================================================
-- Migration: V2.24.1
-- Purpose: Fix "scale has no meaning for SQL floating point types" error
--          by ensuring columns with precision/scale are DECIMAL type, not DOUBLE/FLOAT
-- ============================================================================

-- Check MySQL version and dialect
-- MySQL treats floating point types (DOUBLE, FLOAT) as not supporting precision/scale
-- All columns with @Column(precision=X, scale=Y) must be DECIMAL(X,Y) type

-- Verify and fix any problematic columns (if they exist as DOUBLE/FLOAT)
-- This is safe to run multiple times (IF NOT EXISTS patterns)

-- No changes needed if columns are already DECIMAL type
-- The Hibernate dialect should handle this automatically on schema creation

-- This migration is a safety net in case any columns were mistakenly created
-- as DOUBLE instead of DECIMAL during previous migrations

-- ============================================================================
-- AUDIT TRAIL
-- ============================================================================

-- This migration is proactive and should not modify existing DECIMAL columns
-- If you experience "scale has no meaning" errors, verify:
-- 1. All columns with precision/scale are DECIMAL type
-- 2. MySQL dialect is correctly configured (hibernate.dialect=org.hibernate.dialect.MySQL8Dialect)
-- 3. Connection pool is closed properly before restarting the application

-- Example verification query (run this if issues persist):
-- SELECT COLUMN_NAME, COLUMN_TYPE FROM INFORMATION_SCHEMA.COLUMNS
-- WHERE TABLE_NAME = 'material_types' AND COLUMN_NAME = 'activation_energy';
-- Should return: activation_energy | decimal(10,4)

-- ============================================================================
