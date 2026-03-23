-- Migracja V2.26.0: Zapewnienie spójności ról i dodanie ROLE_USER
-- GxP Compliance: Ścieżka audytu dla ról systemowych

INSERT IGNORE INTO roles (name) VALUES ('ROLE_USER');
INSERT IGNORE INTO roles (name) VALUES ('ROLE_COMPANY_ADMIN');
INSERT IGNORE INTO roles (name) VALUES ('ROLE_SUPER_ADMIN');

-- Upewnij się, że opisy ról są poprawne (jeśli kolumna description istnieje)
-- ALTER TABLE roles ADD COLUMN IF NOT EXISTS description VARCHAR(255);
-- UPDATE roles SET description = 'Użytkownik standardowy' WHERE name = 'ROLE_USER';
-- UPDATE roles SET description = 'Administrator firmy' WHERE name = 'ROLE_COMPANY_ADMIN';
