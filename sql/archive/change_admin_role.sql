-- Remove ROLE_ADMIN from admin user
DELETE FROM user_roles
WHERE user_id = (SELECT id FROM users WHERE username = 'admin')
AND role_id = (SELECT id FROM roles WHERE name = 'ROLE_ADMIN');

-- Add ROLE_SUPER_ADMIN to admin user
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
CROSS JOIN roles r
WHERE u.username = 'admin'
AND r.name = 'ROLE_SUPER_ADMIN'
AND NOT EXISTS (
    SELECT 1 FROM user_roles ur
    WHERE ur.user_id = u.id AND ur.role_id = r.id
);

-- Verify
SELECT 'Admin user has these roles:' as status;
SELECT r.id, r.name FROM user_roles ur
JOIN roles r ON ur.role_id = r.id
WHERE ur.user_id = (SELECT id FROM users WHERE username = 'admin');
