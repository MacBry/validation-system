-- ============================================================================
-- ADD MATERIAL DESCRIPTION TO PLAN ACCEPTANCE CRITERIA
-- ============================================================================
-- Migration: V2.28.5
-- Purpose: Add plan_material_description column to validation_plan_data
--          to store information about materials stored in the device
-- ============================================================================

ALTER TABLE validation_plan_data
  ADD COLUMN plan_material_description LONGTEXT NULL
    COMMENT 'Opis materiałów/zawartości przechowywanych w urządzeniu (np. leki wrażliwe na temperaturę)'
    AFTER plan_drift_max_temp;

-- ============================================================================
-- VERIFICATION
-- ============================================================================
SELECT CONCAT(
    'V2.28.5: Added plan_material_description column to validation_plan_data. At ',
    NOW()
) AS migration_status;
