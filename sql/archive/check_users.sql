SELECT 'ROLE' as 'Typ', name as 'Nazwa' FROM roles
UNION ALL
SELECT 'USER', username FROM users
UNION ALL
SELECT 'PERMISSION', CONCAT('user_id=', user_id, ' company_id=', company_id) FROM user_permissions
ORDER BY `Typ`, `Nazwa`;
