-- Fix for Data truncated for column 'status' in thermo_recorders
-- Changes status from potentially short VARCHAR or ENUM to VARCHAR(50) 
-- to safely accommodate 'UNDER_CALIBRATION' status (17 chars) and any future statuses.

ALTER TABLE `thermo_recorders` MODIFY COLUMN `status` VARCHAR(50) NOT NULL;
ALTER TABLE `thermo_recorders_aud` MODIFY COLUMN `status` VARCHAR(50) NULL;
