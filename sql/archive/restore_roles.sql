-- Restore roles and admin user
-- Roles are already in the database (from V2.12.0 migration)

INSERT INTO `users` (
    `username`,
    `email`,
    `password`,
    `enabled`,
    `locked`,
    `account_expired`,
    `credentials_expired`,
    `failed_login_attempts`,
    `first_name`,
    `last_name`,
    `created_date`,
    `must_change_password`,
    `password_changed_at`,
    `password_expiry_days`
) VALUES (
    'admin',
    'admin@rckik.pl',
    '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYIpEvKBqe2',
    TRUE,
    FALSE,
    FALSE,
    FALSE,
    0,
    'System',
    'Administrator',
    NOW(),
    FALSE,
    NOW(),
    90
);

INSERT INTO `user_roles` (`user_id`, `role_id`, `granted_date`)
SELECT u.`id`, r.`id`, NOW()
FROM `users` u
CROSS JOIN `roles` r
WHERE u.`username` = 'admin'
  AND r.`name` = 'ROLE_SUPER_ADMIN';

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
    u.`id` AS granted_by,
    NOW() AS granted_date
FROM `users` u
CROSS JOIN `companies` c
WHERE u.`username` = 'admin';

SELECT 'Role i użytkownicy przywróceni!' as status;
