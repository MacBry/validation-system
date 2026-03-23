-- ============================================================================
-- VALIDATION SYSTEM v2.12.0 - ENTERPRISE SECURITY MIGRATION
-- ============================================================================
-- Autor: Maciej + Claude
-- Data: 2026-02-22
-- Opis: Implementacja systemu autentykacji, autoryzacji i multi-tenancy
-- Wersja: 2.12.0-ENTERPRISE
-- ============================================================================
-- ZAWARTOŚĆ:
-- 1. Users & Authentication (4 tabele)
-- 2. Audit & Monitoring (2 tabele)
-- 3. Password Recovery (1 tabela)
-- 4. Views (2 widoki)
-- 5. Initial Data (Super Admin + Roles)
-- ============================================================================

-- Set character set
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- ============================================================================
-- 1. USERS TABLE - główna tabela użytkowników
-- ============================================================================

CREATE TABLE IF NOT EXISTS `users` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,

    -- Credentials
    `username` VARCHAR(50) UNIQUE NOT NULL,
    `email` VARCHAR(100) UNIQUE NOT NULL,
    `password` VARCHAR(255) NOT NULL COMMENT 'BCrypt hash (cost 12)',

    -- Account status
    `enabled` BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Czy konto jest aktywne',
    `locked` BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Czy konto jest zablokowane',
    `account_expired` BOOLEAN NOT NULL DEFAULT FALSE,
    `credentials_expired` BOOLEAN NOT NULL DEFAULT FALSE,

    -- Security fields
    `failed_login_attempts` INT NOT NULL DEFAULT 0,
    `locked_until` DATETIME NULL COMMENT 'Czas do którego konto jest zablokowane',

    -- Personal info
    `first_name` VARCHAR(100) NULL,
    `last_name` VARCHAR(100) NULL,
    `phone` VARCHAR(20) NULL,

    -- Timestamps
    `created_date` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_date` DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    `last_login` DATETIME NULL,

    -- Password management (created here in security schema to match User entity)
    `must_change_password` BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Czy użytkownik musi zmienić hasło',
    `password_changed_at` DATETIME NULL COMMENT 'Czas ostatniej zmiany hasła',
    `password_expires_at` DATETIME NULL COMMENT 'Data wygaśnięcia hasła',
    `password_expiry_days` INT DEFAULT 90 COMMENT 'Dni do wygaśnięcia hasła',

    -- Auditing
    `created_by` BIGINT NULL COMMENT 'User ID który utworzył konto',

    -- ENTERPRISE FIX #1: Permissions cache (JSON)
    `permissions_cache_json` JSON NULL COMMENT 'Cache uprawnień - eliminuje LazyInitializationException',

    -- Indexes
    INDEX `idx_username` (`username`),
    INDEX `idx_email` (`email`),
    INDEX `idx_enabled` (`enabled`),
    INDEX `idx_locked` (`locked`),
    INDEX `idx_created_date` (`created_date`),

    -- Constraints
    CONSTRAINT `chk_username_length` CHECK (CHAR_LENGTH(`username`) >= 3),
    CONSTRAINT `chk_email_format` CHECK (`email` LIKE '%@%')

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Użytkownicy systemu - implementuje UserDetails dla Spring Security';

-- ============================================================================
-- 2. ROLES TABLE - role użytkowników
-- ============================================================================

CREATE TABLE IF NOT EXISTS `roles` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `name` VARCHAR(50) UNIQUE NOT NULL COMMENT 'ROLE_SUPER_ADMIN, ROLE_COMPANY_ADMIN, ROLE_USER',
    `description` VARCHAR(255) NULL,
    `created_date` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
    CONSTRAINT `chk_role_prefix` CHECK (`name` LIKE 'ROLE_%')

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Role systemowe - RBAC';

-- ============================================================================
-- 3. USER_ROLES TABLE - przypisanie ról (Many-to-Many)
-- ============================================================================

CREATE TABLE IF NOT EXISTS `user_roles` (
    `user_id` BIGINT NOT NULL,
    `role_id` BIGINT NOT NULL,
    `granted_date` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `granted_by` BIGINT NULL COMMENT 'User ID który nadał rolę',

    PRIMARY KEY (`user_id`, `role_id`),

    FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`role_id`) REFERENCES `roles`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`granted_by`) REFERENCES `users`(`id`) ON DELETE SET NULL,

    INDEX `idx_user` (`user_id`),
    INDEX `idx_role` (`role_id`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Przypisanie ról do użytkowników';

-- ============================================================================
-- 4. USER_PERMISSIONS TABLE - granularne uprawnienia (Multi-Tenancy)
-- ============================================================================

CREATE TABLE IF NOT EXISTS `user_permissions` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,

    -- User reference
    `user_id` BIGINT NOT NULL,

    -- Scope
    `company_id` BIGINT NOT NULL COMMENT 'Firma do której dotyczy uprawnienie',

    -- Permission type
    `permission_type` ENUM('FULL_COMPANY', 'FULL_DEPARTMENT', 'SPECIFIC_LABORATORY') NOT NULL,

    -- Optional: zależne od permission_type
    `department_id` BIGINT NULL COMMENT 'NULL jeśli FULL_COMPANY',
    `laboratory_id` BIGINT NULL COMMENT 'NULL jeśli FULL_COMPANY lub FULL_DEPARTMENT',

    -- Metadata
    `granted_by` BIGINT NULL COMMENT 'User ID który nadał uprawnienie',
    `granted_date` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `expires_date` DATETIME NULL COMMENT 'Opcjonalnie: data wygaśnięcia uprawnienia',

    -- Foreign Keys
    FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`company_id`) REFERENCES `companies`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`department_id`) REFERENCES `departments`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`laboratory_id`) REFERENCES `laboratories`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`granted_by`) REFERENCES `users`(`id`) ON DELETE SET NULL,

    -- Unique constraint - nie może być duplikatów
    UNIQUE KEY `unique_permission` (`user_id`, `company_id`, `department_id`, `laboratory_id`),

    -- Business rules validation
    CONSTRAINT `chk_full_company` CHECK (
        `permission_type` != 'FULL_COMPANY' OR
        (`department_id` IS NULL AND `laboratory_id` IS NULL)
    ),
    CONSTRAINT `chk_full_department` CHECK (
        `permission_type` != 'FULL_DEPARTMENT' OR
        (`department_id` IS NOT NULL AND `laboratory_id` IS NULL)
    ),
    CONSTRAINT `chk_specific_lab` CHECK (
        `permission_type` != 'SPECIFIC_LABORATORY' OR
        (`department_id` IS NOT NULL AND `laboratory_id` IS NOT NULL)
    ),

    -- Indexes dla wydajności (ENTERPRISE FIX #2)
    INDEX `idx_user` (`user_id`),
    INDEX `idx_company` (`company_id`),
    INDEX `idx_department` (`department_id`),
    INDEX `idx_laboratory` (`laboratory_id`),
    INDEX `idx_permission_type` (`permission_type`),
    INDEX `idx_user_company` (`user_id`, `company_id`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Granularne uprawnienia użytkowników - Multi-Tenancy (Company→Department→Laboratory)';

-- ============================================================================
-- 5. AUDIT_LOG TABLE - compliance & security monitoring
-- ============================================================================

CREATE TABLE IF NOT EXISTS `audit_log` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,

    -- Who
    `user_id` BIGINT NULL COMMENT 'User który wykonał operację (NULL = system)',
    `username` VARCHAR(50) NULL COMMENT 'Username w momencie operacji (denormalizacja)',

    -- What
    `entity_type` VARCHAR(100) NOT NULL COMMENT 'CoolingDevice, Validation, etc.',
    `entity_id` BIGINT NOT NULL COMMENT 'ID zmienionego rekordu',
    `action` VARCHAR(50) NOT NULL COMMENT 'CREATE, UPDATE, DELETE, LOGIN, LOGOUT',

    -- Changes (LEGACY TEXT - deprecated)
    `old_value` TEXT NULL COMMENT 'DEPRECATED - używaj old_value_json',
    `new_value` TEXT NULL COMMENT 'DEPRECATED - używaj new_value_json',

    -- ENTERPRISE FIX #4: JSON columns dla zaawansowanych queries
    `old_value_json` JSON NULL COMMENT 'Poprzednie wartości (JSON)',
    `new_value_json` JSON NULL COMMENT 'Nowe wartości (JSON)',

    -- When & Where
    `timestamp` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `ip_address` VARCHAR(45) NULL COMMENT 'IPv4 lub IPv6',
    `user_agent` VARCHAR(500) NULL COMMENT 'Browser/client info',

    -- Context
    `session_id` VARCHAR(100) NULL,
    `request_url` VARCHAR(500) NULL,

    -- Foreign Keys (SET NULL - audit log musi przetrwać usunięcie usera)
    FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE SET NULL,

    -- Indexes (ENTERPRISE FIX #4 - dla JSON queries)
    INDEX `idx_entity` (`entity_type`, `entity_id`),
    INDEX `idx_user` (`user_id`),
    INDEX `idx_timestamp` (`timestamp` DESC),
    INDEX `idx_action` (`action`),
    INDEX `idx_entity_timestamp` (`entity_type`, `entity_id`, `timestamp` DESC)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Audit trail - wszystkie operacje w systemie (GMP compliance)';

-- ============================================================================
-- 6. LOGIN_HISTORY TABLE - security monitoring
-- ============================================================================

CREATE TABLE IF NOT EXISTS `login_history` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,

    `user_id` BIGINT NULL,
    `username` VARCHAR(50) NOT NULL COMMENT 'Username użyty do logowania',

    -- Login details
    `login_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `ip_address` VARCHAR(45) NULL,
    `user_agent` VARCHAR(500) NULL,

    -- Result
    `success` BOOLEAN NOT NULL,
    `failure_reason` VARCHAR(255) NULL COMMENT 'Bad credentials, Account locked, etc.',

    -- Session
    `session_id` VARCHAR(100) NULL,

    FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE SET NULL,

    -- Indexes
    INDEX `idx_user_time` (`user_id`, `login_time` DESC),
    INDEX `idx_username` (`username`),
    INDEX `idx_success` (`success`),
    INDEX `idx_timestamp` (`login_time` DESC),
    INDEX `idx_ip_time` (`ip_address`, `login_time` DESC)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Historia logowań - brute force detection';

-- ============================================================================
-- 7. PASSWORD_RESET_TOKENS TABLE (Future feature)
-- ============================================================================

CREATE TABLE IF NOT EXISTS `password_reset_tokens` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `token` VARCHAR(255) UNIQUE NOT NULL,
    `expires_date` DATETIME NOT NULL,
    `used` BOOLEAN NOT NULL DEFAULT FALSE,
    `created_date` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE,

    INDEX `idx_token` (`token`),
    INDEX `idx_expires` (`expires_date`),
    INDEX `idx_user_used` (`user_id`, `used`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Tokeny resetowania hasła (future feature)';

-- ============================================================================
-- 8. INITIAL DATA - ROLES
-- ============================================================================

INSERT IGNORE INTO `roles` (`name`, `description`) VALUES
('ROLE_SUPER_ADMIN', 'Super Administrator - pełen dostęp do całego systemu'),
('ROLE_COMPANY_ADMIN', 'Administrator Firmy - pełen dostęp do swojej firmy'),
('ROLE_USER', 'Użytkownik - dostęp do przypisanych działów/pracowni');

-- ============================================================================
-- 9. INITIAL DATA - SUPER ADMIN USER
-- ============================================================================
-- BCrypt hash (cost 12): $2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYIpEvKBqe2

INSERT IGNORE INTO `users` (
    `username`,
    `email`,
    `password`,
    `enabled`,
    `first_name`,
    `last_name`,
    `created_date`
) VALUES (
    'admin',
    'admin@rckik.pl',
    '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYIpEvKBqe2',
    TRUE,
    'System',
    'Administrator',
    NOW()
);

-- ============================================================================
-- 10. INITIAL DATA - SUPER ADMIN ROLE ASSIGNMENT (IDEMPOTENT)
-- ============================================================================

-- Add granted_date column if missing (idempotent migration)
SET @dbname = DATABASE();
SET @tablename = 'user_roles';
SET @columnname = 'granted_date';
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname
   AND TABLE_NAME = @tablename
   AND COLUMN_NAME = @columnname) = 0,
  CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @columnname, ' DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP'),
  'SELECT 1'
));
PREPARE stmt FROM @preparedStatement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add granted_by column if missing
SET @columnname2 = 'granted_by';
SET @preparedStatement2 = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @dbname
   AND TABLE_NAME = @tablename
   AND COLUMN_NAME = @columnname2) = 0,
  CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @columnname2, ' BIGINT NULL COMMENT "User ID który nadał rolę"'),
  'SELECT 1'
));
PREPARE stmt2 FROM @preparedStatement2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- Now insert the admin role assignment
INSERT IGNORE INTO `user_roles` (`user_id`, `role_id`, `granted_date`)
SELECT u.`id`, r.`id`, NOW()
FROM `users` u
CROSS JOIN `roles` r
WHERE u.`username` = 'admin'
  AND r.`name` = 'ROLE_SUPER_ADMIN';

-- ============================================================================
-- 11. INITIAL DATA - SUPER ADMIN PERMISSIONS (all companies)
-- ============================================================================
-- Super Admin automatycznie dostaje FULL_COMPANY dla każdej istniejącej firmy

INSERT INTO `user_permissions` (
    `user_id`,
    `company_id`,
    `permission_type`,
    `granted_by`,
    `granted_date`
)
SELECT
    u.`id` AS user_id,
    c.`id` AS company_id,
    'FULL_COMPANY' AS permission_type,
    u.`id` AS granted_by,  -- Self-granted
    NOW() AS granted_date
FROM `users` u
CROSS JOIN `companies` c
WHERE u.`username` = 'admin';

-- ============================================================================
-- 12. VIEWS - Helper views dla łatwiejszych zapytań
-- ============================================================================

-- View: Users z rolami (agregacja)
CREATE OR REPLACE VIEW `v_users_with_roles` AS
SELECT
    u.`id`,
    u.`username`,
    u.`email`,
    u.`first_name`,
    u.`last_name`,
    u.`enabled`,
    u.`locked`,
    u.`created_date`,
    u.`last_login`,
    GROUP_CONCAT(r.`name` ORDER BY r.`name` SEPARATOR ', ') AS `roles`
FROM `users` u
LEFT JOIN `user_roles` ur ON u.`id` = ur.`user_id`
LEFT JOIN `roles` r ON ur.`role_id` = r.`id`
GROUP BY
    u.`id`,
    u.`username`,
    u.`email`,
    u.`first_name`,
    u.`last_name`,
    u.`enabled`,
    u.`locked`,
    u.`created_date`,
    u.`last_login`;

-- View: User permissions w czytelnej formie
CREATE OR REPLACE VIEW `v_user_permissions_readable` AS
SELECT
    up.`id`,
    up.`user_id`,
    u.`username`,
    c.`name` AS company_name,
    up.`permission_type`,
    d.`name` AS department_name,
    d.`abbreviation` AS department_abbr,
    l.`full_name` AS laboratory_name,
    l.`abbreviation` AS laboratory_abbr,
    up.`granted_date`,
    up.`expires_date`,
    granter.`username` AS granted_by_username,
    CASE
        WHEN up.`expires_date` IS NULL THEN TRUE
        WHEN up.`expires_date` > NOW() THEN TRUE
        ELSE FALSE
    END AS is_active
FROM `user_permissions` up
INNER JOIN `users` u ON up.`user_id` = u.`id`
INNER JOIN `companies` c ON up.`company_id` = c.`id`
LEFT JOIN `departments` d ON up.`department_id` = d.`id`
LEFT JOIN `laboratories` l ON up.`laboratory_id` = l.`id`
LEFT JOIN `users` granter ON up.`granted_by` = granter.`id`;

-- ============================================================================
-- 13. VERIFICATION QUERIES
-- ============================================================================

-- Verify tables created
SELECT
    TABLE_NAME,
    ENGINE,
    TABLE_ROWS,
    AVG_ROW_LENGTH,
    DATA_LENGTH,
    CREATE_TIME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN (
      'users',
      'roles',
      'user_roles',
      'user_permissions',
      'audit_log',
      'login_history',
      'password_reset_tokens'
  )
ORDER BY TABLE_NAME;

-- Verify super admin exists
SELECT
    u.id,
    u.username,
    u.email,
    u.enabled,
    GROUP_CONCAT(r.name) AS roles,
    COUNT(DISTINCT up.id) AS permission_count
FROM users u
LEFT JOIN user_roles ur ON u.id = ur.user_id
LEFT JOIN roles r ON ur.role_id = r.id
LEFT JOIN user_permissions up ON u.id = up.user_id
WHERE u.username = 'admin'
GROUP BY u.id, u.username, u.email, u.enabled;

-- Verify indexes
SELECT
    TABLE_NAME,
    INDEX_NAME,
    GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS columns,
    INDEX_TYPE,
    NON_UNIQUE
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('users', 'user_permissions', 'audit_log', 'login_history')
GROUP BY TABLE_NAME, INDEX_NAME, INDEX_TYPE, NON_UNIQUE
ORDER BY TABLE_NAME, INDEX_NAME;

-- ============================================================================
-- MIGRATION COMPLETE
-- ============================================================================

SELECT '========================================' AS '';
SELECT 'VALIDATION SYSTEM v2.12.0' AS '';
SELECT 'SECURITY MIGRATION COMPLETED' AS '';
SELECT '========================================' AS '';
SELECT CONCAT('Database: ', DATABASE()) AS '';
SELECT CONCAT('Tables created: 7') AS '';
SELECT CONCAT('Views created: 2') AS '';
SELECT CONCAT('Initial users: 1 (admin)') AS '';
SELECT CONCAT('Initial roles: 3') AS '';
SELECT CONCAT('Migration date: ', NOW()) AS '';
SELECT '========================================' AS '';
SELECT 'NEXT STEPS:' AS '';
SELECT '1. Verify super admin login via application UI' AS '';
SELECT '2. Test Redis connection' AS '';
SELECT '3. Maven build & compile' AS '';
SELECT '4. Start Spring Boot application' AS '';
SELECT '========================================' AS '';
