-- ============================================================================
-- Password Expiry System - v2.19.0
-- ============================================================================
-- Description: Add password expiry functionality to enforce periodic password changes
-- Compliance: GMP Annex 11 - Password aging and security controls
-- ============================================================================

-- Add password expiry tracking to users table
ALTER TABLE users
ADD COLUMN password_expires_at DATETIME NULL COMMENT 'Data wygaśnięcia hasła (null = bez wygasania)';

-- Add password expiry policy configuration
ALTER TABLE users
ADD COLUMN password_expiry_days INT DEFAULT 90 COMMENT 'Liczba dni ważności hasła (90 dni domyślnie)';

-- Add index for efficient password expiry queries
CREATE INDEX idx_users_password_expires_at ON users(password_expires_at);

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