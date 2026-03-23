-- ============================================================================
-- Migracja v2.11.0: Struktura organizacyjna Firma → Dział → Pracownia
-- Data: 2026-02-19
-- Opis: Wprowadzenie 3-poziomowej hierarchii organizacyjnej
-- ============================================================================

USE validation_system;

-- ============================================================================
-- KROK 1: Tworzenie tabeli companies
-- ============================================================================
CREATE TABLE IF NOT EXISTS companies (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(500),
    created_date DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_company_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SELECT '✅ Krok 1: Utworzono tabelę companies' AS Status;

-- ============================================================================
-- KROK 2: Tworzenie tabeli departments
-- ============================================================================
CREATE TABLE IF NOT EXISTS departments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    abbreviation VARCHAR(20) NOT NULL UNIQUE,
    description TEXT,
    has_laboratories BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (company_id) REFERENCES companies(id),
    INDEX idx_department_company (company_id),
    INDEX idx_department_abbreviation (abbreviation)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SELECT '✅ Krok 2: Utworzono tabelę departments' AS Status;

-- ============================================================================
-- KROK 3: Dodanie department_id do laboratories
-- ============================================================================
-- Sprawdź czy kolumna już istnieje
SET @column_exists_lab = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'validation_system'
    AND TABLE_NAME = 'laboratories'
    AND COLUMN_NAME = 'department_id'
);

-- Dodaj kolumnę jeśli nie istnieje
SET @sql_lab = IF(@column_exists_lab = 0,
    'ALTER TABLE laboratories ADD COLUMN department_id BIGINT AFTER id',
    'SELECT ''Kolumna department_id w laboratories już istnieje'' AS Info'
);

PREPARE stmt FROM @sql_lab;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Dodaj foreign key
SET @fk_exists_lab = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = 'validation_system'
    AND TABLE_NAME = 'laboratories'
    AND CONSTRAINT_NAME = 'fk_laboratory_department'
);

SET @sql_fk_lab = IF(@fk_exists_lab = 0,
    'ALTER TABLE laboratories ADD CONSTRAINT fk_laboratory_department FOREIGN KEY (department_id) REFERENCES departments(id)',
    'SELECT ''Foreign key fk_laboratory_department już istnieje'' AS Info'
);

PREPARE stmt FROM @sql_fk_lab;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT '✅ Krok 3: Zaktualizowano tabelę laboratories' AS Status;

-- ============================================================================
-- KROK 4: Dodanie department_id do cooling_devices i zmiana laboratory_id na NULLABLE
-- ============================================================================
-- Dodaj department_id
SET @column_exists_dev = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'validation_system'
    AND TABLE_NAME = 'cooling_devices'
    AND COLUMN_NAME = 'department_id'
);

SET @sql_dev = IF(@column_exists_dev = 0,
    'ALTER TABLE cooling_devices ADD COLUMN department_id BIGINT AFTER id',
    'SELECT ''Kolumna department_id w cooling_devices już istnieje'' AS Info'
);

PREPARE stmt FROM @sql_dev;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Zmień laboratory_id na NULLABLE
ALTER TABLE cooling_devices MODIFY COLUMN laboratory_id BIGINT NULL;

-- Dodaj foreign key
SET @fk_exists_dev = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = 'validation_system'
    AND TABLE_NAME = 'cooling_devices'
    AND CONSTRAINT_NAME = 'fk_device_department'
);

SET @sql_fk_dev = IF(@fk_exists_dev = 0,
    'ALTER TABLE cooling_devices ADD CONSTRAINT fk_device_department FOREIGN KEY (department_id) REFERENCES departments(id)',
    'SELECT ''Foreign key fk_device_department już istnieje'' AS Info'
);

PREPARE stmt FROM @sql_fk_dev;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT '✅ Krok 4: Zaktualizowano tabelę cooling_devices' AS Status;

-- ============================================================================
-- KROK 5: Dodanie department_id i laboratory_id do thermo_recorders
-- ============================================================================
-- Dodaj department_id
SET @column_exists_rec_dept = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'validation_system'
    AND TABLE_NAME = 'thermo_recorders'
    AND COLUMN_NAME = 'department_id'
);

SET @sql_rec_dept = IF(@column_exists_rec_dept = 0,
    'ALTER TABLE thermo_recorders ADD COLUMN department_id BIGINT AFTER id',
    'SELECT ''Kolumna department_id w thermo_recorders już istnieje'' AS Info'
);

PREPARE stmt FROM @sql_rec_dept;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Dodaj laboratory_id
SET @column_exists_rec_lab = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = 'validation_system'
    AND TABLE_NAME = 'thermo_recorders'
    AND COLUMN_NAME = 'laboratory_id'
);

SET @sql_rec_lab = IF(@column_exists_rec_lab = 0,
    'ALTER TABLE thermo_recorders ADD COLUMN laboratory_id BIGINT AFTER department_id',
    'SELECT ''Kolumna laboratory_id w thermo_recorders już istnieje'' AS Info'
);

PREPARE stmt FROM @sql_rec_lab;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Dodaj foreign keys
SET @fk_exists_rec_dept = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = 'validation_system'
    AND TABLE_NAME = 'thermo_recorders'
    AND CONSTRAINT_NAME = 'fk_recorder_department'
);

SET @sql_fk_rec_dept = IF(@fk_exists_rec_dept = 0,
    'ALTER TABLE thermo_recorders ADD CONSTRAINT fk_recorder_department FOREIGN KEY (department_id) REFERENCES departments(id)',
    'SELECT ''Foreign key fk_recorder_department już istnieje'' AS Info'
);

PREPARE stmt FROM @sql_fk_rec_dept;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_exists_rec_lab = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = 'validation_system'
    AND TABLE_NAME = 'thermo_recorders'
    AND CONSTRAINT_NAME = 'fk_recorder_laboratory'
);

SET @sql_fk_rec_lab = IF(@fk_exists_rec_lab = 0,
    'ALTER TABLE thermo_recorders ADD CONSTRAINT fk_recorder_laboratory FOREIGN KEY (laboratory_id) REFERENCES laboratories(id)',
    'SELECT ''Foreign key fk_recorder_laboratory już istnieje'' AS Info'
);

PREPARE stmt FROM @sql_fk_rec_lab;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT '✅ Krok 5: Zaktualizowano tabelę thermo_recorders' AS Status;

-- ============================================================================
-- KROK 6: Wstawianie danych - Firma RCKiK w Poznaniu
-- ============================================================================
INSERT IGNORE INTO companies (id, name, address) 
VALUES (1, 'Regionalne Centrum Krwiodawstwa i Krwiolecznictwa w Poznaniu', 'ul. Marcelińska 44, 60-354 Poznań');

SELECT '✅ Krok 6: Utworzono firmę RCKiK w Poznaniu' AS Status;

-- ============================================================================
-- KROK 7: Wstawianie danych - Główne działy z schematu organizacyjnego
-- ============================================================================
INSERT IGNORE INTO departments (company_id, name, abbreviation, has_laboratories, description) VALUES
(1, 'Dział Laboratoryjny', 'LAB', TRUE, 'Dział laboratoryjny z wieloma pracowniami specjalistycznymi'),
(1, 'Dział Immunologii Transfuzjologicznej', 'IMM', TRUE, 'Dział immunologii z pracowniami serologii'),
(1, 'Dział Pobierania', 'POB', FALSE, 'Dział odpowiedzialny za pobieranie krwi od dawców'),
(1, 'Dział Preparatyki', 'PREP', FALSE, 'Dział preparatyki krwi i jej składników'),
(1, 'Dział Ekspedycji', 'EKS', FALSE, 'Dział odpowiedzialny za dystrybucję produktów krwiopochodnych'),
(1, 'Dział Zapewnienia Jakości', 'ZJ', FALSE, 'Dział zapewnienia jakości'),
(1, 'Dział Dawców', 'DAW', FALSE, 'Dział zarządzania bazą dawców'),
(1, 'Dział Marketingu', 'MKT', FALSE, 'Dział marketingu i promocji krwiodawstwa'),
(1, 'Dział Ekonomiczny', 'EKON', FALSE, 'Dział ekonomiczny'),
(1, 'Dział Administracyjno-Gospodarczy', 'ADM', FALSE, 'Dział administracyjno-gospodarczy'),
(1, 'Dział Farmacji Szpitalnej', 'FARM', FALSE, 'Dział farmacji szpitalnej'),
(1, 'Bank Komórek Macierzystych', 'BKM', FALSE, 'Bank komórek macierzystych'),
(1, 'Ośrodek Dawców Szpiku', 'ODS', FALSE, 'Ośrodek Dawców Szpiku');

SELECT '✅ Krok 7: Utworzono główne działy' AS Status;

-- ============================================================================
-- KROK 8: Wstawianie danych - Pracownie Działu Laboratoryjnego
-- ============================================================================
-- Pobierz ID Działu Laboratoryjnego
SET @lab_dept_id = (SELECT id FROM departments WHERE abbreviation = 'LAB');

INSERT IGNORE INTO laboratories (department_id, full_name, abbreviation) VALUES
(@lab_dept_id, 'Pracownia badań z zakresu serologii grup krwi dawców', 'ORS'),
(@lab_dept_id, 'Pracownia badań z zakresu serologii grup krwi pacjentów/biorców', 'PALZPZ'),
(@lab_dept_id, 'Laboratorium Zgodności Tkankowej HLA z Pracownią Diagnostyki Genetycznej', 'HLA'),
(@lab_dept_id, 'Pracownia Badań Czynników Zakaźnych Metodami Biologii Molekularnej', 'MOLBIO'),
(@lab_dept_id, 'Pracownia Badań Czynników Zakaźnych Metodami Serologicznymi', 'SERO'),
(@lab_dept_id, 'Pracownia Analiz Lekarskich z pracownią zewnętrzną', 'PAL'),
(@lab_dept_id, 'Pracownia Diagnostyki Mikrobiologicznej', 'MIKRO');

SELECT '✅ Krok 8: Utworzono pracownie Działu Laboratoryjnego' AS Status;

-- ============================================================================
-- KROK 9: Wstawianie danych - Pracownie Działu Immunologii
-- ============================================================================
-- Pobierz ID Działu Immunologii
SET @imm_dept_id = (SELECT id FROM departments WHERE abbreviation = 'IMM');

INSERT IGNORE INTO laboratories (department_id, full_name, abbreviation) VALUES
(@imm_dept_id, 'Pracownia badań z zakresu serologii grup krwi dawców (IMM)', 'IMM-SERD'),
(@imm_dept_id, 'Pracownia badań z zakresu serologii grup krwi pacjentów/biorców (IMM)', 'IMM-SERP');

SELECT '✅ Krok 9: Utworzono pracownie Działu Immunologii' AS Status;

-- ============================================================================
-- PODSUMOWANIE MIGRACJI
-- ============================================================================
SELECT 
    '========================================' AS ' ',
    'PODSUMOWANIE MIGRACJI v2.11.0' AS ' ',
    '========================================' AS ' ';

SELECT 
    COUNT(*) as total_companies,
    (SELECT name FROM companies LIMIT 1) as company_name
FROM companies;

SELECT 
    COUNT(*) as total_departments,
    SUM(CASE WHEN has_laboratories = TRUE THEN 1 ELSE 0 END) as departments_with_labs,
    SUM(CASE WHEN has_laboratories = FALSE THEN 1 ELSE 0 END) as departments_without_labs
FROM departments;

SELECT 
    COUNT(*) as total_laboratories
FROM laboratories;

SELECT '✅ MIGRACJA ZAKOŃCZONA POMYŚLNIE!' AS Status;

-- ============================================================================
-- UWAGA: Po migracji należy ręcznie przypisać department_id do istniejących:
-- - cooling_devices
-- - thermo_recorders
-- (jeśli istnieją w bazie)
-- ============================================================================
