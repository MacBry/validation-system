-- ============================================================================
-- VALIDATION SYSTEM v2.11.0 - BASE SCHEMA INITIALIZATION
-- ============================================================================
-- Purpose: Create missing core tables required by later security migrations
-- Includes: Organization, Inventory, and Measurement modules
-- ============================================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- ============================================================================
-- 1. BASE ORGANIZATIONAL STRUCTURE
-- ============================================================================

CREATE TABLE IF NOT EXISTS `companies` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `name` VARCHAR(255) NOT NULL,
    `address` VARCHAR(500),
    `created_date` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_company_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `departments` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `company_id` BIGINT NOT NULL,
    `name` VARCHAR(255) NOT NULL,
    `abbreviation` VARCHAR(20) NOT NULL UNIQUE,
    `description` TEXT,
    `has_laboratories` BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (`company_id`) REFERENCES `companies`(`id`),
    INDEX `idx_department_company` (`company_id`),
    INDEX `idx_department_abbreviation` (`abbreviation`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `laboratories` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `department_id` BIGINT NOT NULL,
    `full_name` VARCHAR(200) NOT NULL UNIQUE,
    `abbreviation` VARCHAR(50) NOT NULL UNIQUE,
    FOREIGN KEY (`department_id`) REFERENCES `departments`(`id`),
    INDEX `idx_lab_department` (`department_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- 2. MATERIAL TYPES & INVENTORY
-- ============================================================================

CREATE TABLE IF NOT EXISTS `material_types` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `company_id` BIGINT NULL,
    `name` VARCHAR(100) NOT NULL,
    `description` VARCHAR(500),
    `min_storage_temp` DOUBLE,
    `max_storage_temp` DOUBLE,
    `activation_energy` DECIMAL(10,4),
    `standard_source` VARCHAR(255),
    `application` VARCHAR(255),
    `active` BOOLEAN NOT NULL DEFAULT TRUE,
    FOREIGN KEY (`company_id`) REFERENCES `companies`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cooling_devices` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `inventory_number` VARCHAR(50) NOT NULL UNIQUE,
    `name` VARCHAR(200) NOT NULL,
    `department_id` BIGINT NOT NULL,
    `laboratory_id` BIGINT NULL,
    `chamber_type` VARCHAR(30) NOT NULL,
    `material_type_id` BIGINT NULL,
    `min_operating_temp` DOUBLE,
    `max_operating_temp` DOUBLE,
    `volume_m3` DOUBLE,
    `volume_category` VARCHAR(10),
    `stored_material` VARCHAR(20),
    FOREIGN KEY (`department_id`) REFERENCES `departments`(`id`),
    FOREIGN KEY (`laboratory_id`) REFERENCES `laboratories`(`id`),
    FOREIGN KEY (`material_type_id`) REFERENCES `material_types`(`id`),
    INDEX `idx_device_inventory` (`inventory_number`),
    INDEX `idx_device_dept` (`department_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `thermo_recorders` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `serial_number` VARCHAR(50) NOT NULL UNIQUE,
    `model` VARCHAR(100) NOT NULL,
    `status` VARCHAR(50) NOT NULL,
    `resolution` DECIMAL(4,3) DEFAULT 0.100,
    `department_id` BIGINT NOT NULL,
    `laboratory_id` BIGINT NULL,
    FOREIGN KEY (`department_id`) REFERENCES `departments`(`id`),
    FOREIGN KEY (`laboratory_id`) REFERENCES `laboratories`(`id`),
    INDEX `idx_recorder_serial` (`serial_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `calibrations` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `thermo_recorder_id` BIGINT NOT NULL,
    `calibration_date` DATE NOT NULL,
    `certificate_number` VARCHAR(100) NOT NULL,
    `valid_until` DATE NOT NULL,
    `certificate_file_path` VARCHAR(500),
    FOREIGN KEY (`thermo_recorder_id`) REFERENCES `thermo_recorders`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- 3. MEASUREMENTS & VALIDATIONS
-- ============================================================================

CREATE TABLE IF NOT EXISTS `measurement_series` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `recorder_serial_number` VARCHAR(50),
    `original_filename` VARCHAR(255) NOT NULL,
    `first_measurement_time` DATETIME NOT NULL,
    `last_measurement_time` DATETIME NOT NULL,
    `min_temperature` DOUBLE NOT NULL,
    `max_temperature` DOUBLE NOT NULL,
    `avg_temperature` DOUBLE NOT NULL,
    `std_deviation` DOUBLE,
    `mkt_temperature` DOUBLE,
    `measurement_count` INT NOT NULL,
    `recorder_position` VARCHAR(30),
    `is_reference_recorder` BOOLEAN NOT NULL DEFAULT FALSE,
    `cooling_device_id` BIGINT NULL,
    `thermo_recorder_id` BIGINT NULL,
    `used_in_validation` BOOLEAN NOT NULL DEFAULT FALSE,
    `upload_date` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (`cooling_device_id`) REFERENCES `cooling_devices`(`id`),
    FOREIGN KEY (`thermo_recorder_id`) REFERENCES `thermo_recorders`(`id`),
    INDEX `idx_series_upload` (`upload_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `validations` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `cooling_device_id` BIGINT NOT NULL,
    `validation_plan_number` VARCHAR(20),
    `status` VARCHAR(20),
    `created_date` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `average_device_temperature` DOUBLE,
    `control_sensor_position` VARCHAR(30),
    `device_load_state` VARCHAR(20),
    FOREIGN KEY (`cooling_device_id`) REFERENCES `cooling_devices`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `validation_measurement_series` (
    `validation_id` BIGINT NOT NULL,
    `measurement_series_id` BIGINT NOT NULL,
    PRIMARY KEY (`validation_id`, `measurement_series_id`),
    FOREIGN KEY (`validation_id`) REFERENCES `validations`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`measurement_series_id`) REFERENCES `measurement_series`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `validation_plan_numbers` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `year` INT NOT NULL,
    `plan_number` INT NOT NULL,
    `cooling_device_id` BIGINT NOT NULL,
    FOREIGN KEY (`cooling_device_id`) REFERENCES `cooling_devices`(`id`) ON DELETE CASCADE,
    UNIQUE KEY `unique_plan_year` (`cooling_device_id`, `year`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- 4. INITIAL DATA SEEDING (Organizational)
-- ============================================================================

-- Default Company
INSERT IGNORE INTO `companies` (id, name, address) 
VALUES (1, 'Regionalne Centrum Krwiodawstwa i Krwiolecznictwa w Poznaniu', 'ul. Marcelińska 44, 60-354 Poznań');

-- Default Departments
INSERT IGNORE INTO `departments` (company_id, name, abbreviation, has_laboratories) VALUES
(1, 'Dział Laboratoryjny', 'LAB', TRUE),
(1, 'Dział Immunologii Transfuzjologicznej', 'IMM', TRUE),
(1, 'Dział Pobierania', 'POB', FALSE),
(1, 'Dział Preparatyki', 'PREP', FALSE),
(1, 'Dział Ekspedycji', 'EKS', FALSE),
(1, 'Dział Zapewnienia Jakości', 'ZJ', FALSE);
