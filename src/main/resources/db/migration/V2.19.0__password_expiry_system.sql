-- ============================================================================
-- Password Expiry System - v2.19.0
-- ============================================================================
-- Description: Add password expiry functionality to enforce periodic password changes
-- Compliance: GMP Annex 11 - Password aging and security controls
-- NOTE: Columns created in V2.12.0 security schema - this migration is idempotent
-- ============================================================================

-- Add password expiry tracking to users table (idempotent - may exist from V2.12.0)
SET @dbname = DATABASE();
SET @tablename = 'users';

-- Add password_expires_at if it doesn't exist
SET @columnname1 = 'password_expires_at';
SET @preparedStatement1 = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname
   AND TABLE_NAME = @tablename
   AND COLUMN_NAME = @columnname1) = 0,
  CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @columnname1, ' DATETIME NULL COMMENT "Data wygaśnięcia hasła (null = bez wygasania)"'),
  'SELECT 1'
));
PREPARE stmt1 FROM @preparedStatement1;
EXECUTE stmt1;
DEALLOCATE PREPARE stmt1;

-- Add password_expiry_days if it doesn't exist
SET @columnname2 = 'password_expiry_days';
SET @preparedStatement2 = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname
   AND TABLE_NAME = @tablename
   AND COLUMN_NAME = @columnname2) = 0,
  CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @columnname2, ' INT DEFAULT 90 COMMENT "Liczba dni ważności hasła (90 dni domyślnie)"'),
  'SELECT 1'
));
PREPARE stmt2 FROM @preparedStatement2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- Add index for efficient password expiry queries (idempotent)
SET @indexname = 'idx_users_password_expires_at';
SET @preparedStatement3 = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
   WHERE TABLE_SCHEMA = @dbname
   AND TABLE_NAME = @tablename
   AND INDEX_NAME = @indexname) = 0,
  CONCAT('CREATE INDEX ', @indexname, ' ON ', @tablename, '(password_expires_at)'),
  'SELECT 1'
));
PREPARE stmt3 FROM @preparedStatement3;
EXECUTE stmt3;
DEALLOCATE PREPARE stmt3;

-- ============================================================================
-- Initialize password expiry for existing users
-- ============================================================================
-- Set password expiry for existing users (90 days from last password change or now)
UPDATE users
SET password_expires_at = CASE
    WHEN password_changed_at IS NOT NULL
    THEN DATE_ADD(password_changed_at, INTERVAL 90 DAY)
    ELSE DATE_ADD(NOW(), INTERVAL 90 DAY)
END
WHERE password_expires_at IS NULL;

-- ============================================================================
-- Comments for maintenance
-- ============================================================================
-- To find users with expired passwords:
-- SELECT username, password_expires_at FROM users WHERE password_expires_at <= NOW() AND password_expires_at IS NOT NULL;

-- To find users whose passwords expire in next 7 days:
-- SELECT username, password_expires_at, DATEDIFF(password_expires_at, NOW()) as days_until_expiry
-- FROM users WHERE password_expires_at BETWEEN NOW() AND DATE_ADD(NOW(), INTERVAL 7 DAY);