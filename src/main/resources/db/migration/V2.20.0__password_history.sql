-- ============================================================================
-- V2.20.0 - Password History (FDA 21 CFR Part 11 Sec. 11.300(b))
-- ============================================================================
-- Wymogi regulacyjne:
--   FDA 21 CFR Part 11 Sec. 11.300(b) - hasla musza byc unikalne
--   EU GMP Annex 11 Sec. 12.1 - kontrola dostepu i unikalnosc hasel
--   GxP best practice: przechowywanie 12 ostatnich hasel
-- ============================================================================

CREATE TABLE IF NOT EXISTS password_history (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    user_id     BIGINT          NOT NULL,
    password_hash VARCHAR(255)  NOT NULL COMMENT 'BCrypt hash of previous password',
    changed_at  DATETIME(6)     NOT NULL COMMENT 'Timestamp when this password was set',
    changed_by  VARCHAR(50)     NULL     COMMENT 'Username of who initiated the change (self/admin/system)',
    change_type VARCHAR(30)     NOT NULL DEFAULT 'SELF' COMMENT 'SELF, ADMIN_RESET, FORCED, PASSWORD_RESET, INITIAL',
    PRIMARY KEY (id),
    CONSTRAINT fk_password_history_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Index for efficient lookup: last N passwords for a user (ordered by date)
CREATE INDEX idx_password_history_user_changed
    ON password_history (user_id, changed_at DESC);

-- Index for cleanup operations (finding old entries)
CREATE INDEX idx_password_history_changed_at
    ON password_history (changed_at);

-- ============================================================================
-- Seed existing passwords into history for all current users
-- This ensures that after migration, current passwords are already tracked
-- ============================================================================
INSERT INTO password_history (user_id, password_hash, changed_at, changed_by, change_type)
SELECT id, password, COALESCE(password_changed_at, created_date, NOW()), 'SYSTEM_MIGRATION', 'INITIAL'
FROM users
WHERE password IS NOT NULL;
