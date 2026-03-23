-- Add is_reference_recorder column if it doesn't exist
SET @dbname = DATABASE();
SET @tablename = "measurement_series";
SET @columnname = "is_reference_recorder";
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname
   AND TABLE_NAME = @tablename
   AND COLUMN_NAME = @columnname) = 0,
  CONCAT("ALTER TABLE ", @tablename, " ADD COLUMN ", @columnname, " BOOLEAN NOT NULL DEFAULT FALSE"),
  "SELECT 1"
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add control_sensor_position column if it doesn't exist
SET @tablename2 = "validations";
SET @columnname2 = "control_sensor_position";
SET @preparedStatement2 = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname
   AND TABLE_NAME = @tablename2
   AND COLUMN_NAME = @columnname2) = 0,
  CONCAT("ALTER TABLE ", @tablename2, " ADD COLUMN ", @columnname2, " VARCHAR(30)"),
  "SELECT 1"
));
PREPARE stmt2 FROM @preparedStatement2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;
