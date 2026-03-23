-- Create indices if they don't exist
SET @dbname = DATABASE();
SET @tablename = "audit_log";
SET @indexname1 = "idx_audit_log_entity";
SET @indexname2 = "idx_audit_log_timestamp";

-- Check and create first index
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
   WHERE TABLE_SCHEMA = @dbname
   AND TABLE_NAME = @tablename
   AND INDEX_NAME = @indexname1) = 0,
  CONCAT("CREATE INDEX ", @indexname1, " ON ", @tablename, " (entity_type, entity_id)"),
  "SELECT 1"
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Check and create second index
SET @preparedStatement2 = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
   WHERE TABLE_SCHEMA = @dbname
   AND TABLE_NAME = @tablename
   AND INDEX_NAME = @indexname2) = 0,
  CONCAT("CREATE INDEX ", @indexname2, " ON ", @tablename, " (timestamp DESC)"),
  "SELECT 1"
));
PREPARE stmt2 FROM @preparedStatement2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;
