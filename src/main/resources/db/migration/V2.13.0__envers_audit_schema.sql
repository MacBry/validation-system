-- ============================================================================
-- VALIDATION SYSTEM v2.13.0 - HIBERNATE ENVERS AUDIT SCHEMA
-- ============================================================================
-- GMP Annex 11 / 21 CFR Part 11 - Entity Revisioning
-- Tabele: REVINFO + *_AUD dla encji objętych audytem
-- Wykluczone: measurement_points, measurement_series, validation_signatures,
--             validation_documents, users, roles, user_permissions, audit_log
-- ============================================================================

SET NAMES utf8mb4;

-- ============================================================================
-- 1. REVINFO - główna tabela rewizji (wymagana przez Hibernate Envers)
--    Rozszerzona o dane użytkownika dla GMP traceability
-- ============================================================================
CREATE TABLE IF NOT EXISTS `REVINFO` (
    `REV`        INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `REVTSTMP`   BIGINT       NOT NULL                COMMENT 'Timestamp Unix (ms)',
    `user_id`    BIGINT       NULL                    COMMENT 'ID zalogowanego użytkownika',
    `username`   VARCHAR(50)  NULL                    COMMENT 'Login w chwili operacji',
    `full_name`  VARCHAR(200) NULL                    COMMENT 'Imię i nazwisko (denormalizacja)',
    `ip_address` VARCHAR(45)  NULL                    COMMENT 'IP klienta (IPv4/IPv6)',
    INDEX `idx_rev_user`      (`user_id`),
    INDEX `idx_rev_username`  (`username`),
    INDEX `idx_rev_timestamp` (`REVTSTMP` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Envers revision info — GMP Annex 11 audit trail';

-- ============================================================================
-- 2. companies_AUD
-- ============================================================================
CREATE TABLE IF NOT EXISTS `companies_AUD` (
    `REV`          INT          NOT NULL,
    `REVTYPE`      TINYINT      NULL     COMMENT '0=ADD 1=MOD 2=DEL',
    `id`           BIGINT       NOT NULL,
    `name`         VARCHAR(255) NULL,
    `address`      VARCHAR(500) NULL,
    `created_date` DATETIME(6)  NULL,
    PRIMARY KEY (`id`, `REV`),
    CONSTRAINT `fk_companies_aud_rev` FOREIGN KEY (`REV`) REFERENCES `REVINFO`(`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Historia zmian firm';

-- ============================================================================
-- 3. departments_AUD
-- ============================================================================
CREATE TABLE IF NOT EXISTS `departments_AUD` (
    `REV`              INT          NOT NULL,
    `REVTYPE`          TINYINT      NULL,
    `id`               BIGINT       NOT NULL,
    `company_id`       BIGINT       NULL,
    `name`             VARCHAR(255) NULL,
    `abbreviation`     VARCHAR(20)  NULL,
    `description`      TEXT         NULL,
    `has_laboratories` BIT(1)       NULL,
    PRIMARY KEY (`id`, `REV`),
    CONSTRAINT `fk_departments_aud_rev` FOREIGN KEY (`REV`) REFERENCES `REVINFO`(`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Historia zmian działów';

-- ============================================================================
-- 4. laboratories_AUD
-- ============================================================================
CREATE TABLE IF NOT EXISTS `laboratories_AUD` (
    `REV`           INT          NOT NULL,
    `REVTYPE`       TINYINT      NULL,
    `id`            BIGINT       NOT NULL,
    `department_id` BIGINT       NULL,
    `full_name`     VARCHAR(200) NULL,
    `abbreviation`  VARCHAR(50)  NULL,
    PRIMARY KEY (`id`, `REV`),
    CONSTRAINT `fk_laboratories_aud_rev` FOREIGN KEY (`REV`) REFERENCES `REVINFO`(`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Historia zmian pracowni';

-- ============================================================================
-- 5. material_types_AUD
-- ============================================================================
CREATE TABLE IF NOT EXISTS `material_types_AUD` (
    `REV`               INT            NOT NULL,
    `REVTYPE`           TINYINT        NULL,
    `id`                BIGINT         NOT NULL,
    `company_id`        BIGINT         NULL,
    `name`              VARCHAR(100)   NULL,
    `description`       VARCHAR(500)   NULL,
    `min_storage_temp`  DOUBLE         NULL,
    `max_storage_temp`  DOUBLE         NULL,
    `activation_energy` DECIMAL(10,4)  NULL,
    `standard_source`   VARCHAR(255)   NULL,
    `application`       VARCHAR(255)   NULL,
    `active`            BIT(1)         NULL,
    PRIMARY KEY (`id`, `REV`),
    CONSTRAINT `fk_material_types_aud_rev` FOREIGN KEY (`REV`) REFERENCES `REVINFO`(`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Historia zmian typów materiałów';

-- ============================================================================
-- 6. cooling_devices_AUD  (kluczowa dla GMP — zmiany urządzenia wpływają na walidację)
-- ============================================================================
CREATE TABLE IF NOT EXISTS `cooling_devices_AUD` (
    `REV`                INT          NOT NULL,
    `REVTYPE`            TINYINT      NULL,
    `id`                 BIGINT       NOT NULL,
    `inventory_number`   VARCHAR(50)  NULL,
    `name`               VARCHAR(200) NULL,
    `department_id`      BIGINT       NULL,
    `laboratory_id`      BIGINT       NULL,
    `chamber_type`       VARCHAR(30)  NULL,
    `stored_material`    VARCHAR(20)  NULL,
    `material_type_id`   BIGINT       NULL,
    `min_operating_temp` DOUBLE       NULL,
    `max_operating_temp` DOUBLE       NULL,
    PRIMARY KEY (`id`, `REV`),
    CONSTRAINT `fk_cooling_devices_aud_rev` FOREIGN KEY (`REV`) REFERENCES `REVINFO`(`REV`),
    INDEX `idx_cd_aud_id_rev` (`id`, `REV` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Historia zmian urządzeń chłodniczych — kluczowa dla GMP';

-- ============================================================================
-- 7. thermo_recorders_AUD
-- ============================================================================
CREATE TABLE IF NOT EXISTS `thermo_recorders_AUD` (
    `REV`           INT          NOT NULL,
    `REVTYPE`       TINYINT      NULL,
    `id`            BIGINT       NOT NULL,
    `serial_number` VARCHAR(50)  NULL,
    `model`         VARCHAR(100) NULL,
    `status`        VARCHAR(20)  NULL,
    `department_id` BIGINT       NULL,
    `laboratory_id` BIGINT       NULL,
    PRIMARY KEY (`id`, `REV`),
    CONSTRAINT `fk_thermo_recorders_aud_rev` FOREIGN KEY (`REV`) REFERENCES `REVINFO`(`REV`),
    INDEX `idx_tr_aud_id_rev` (`id`, `REV` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Historia zmian rejestratorów temperatur';

-- ============================================================================
-- 8. calibrations_AUD  (wymagane GMP — wzorcowanie to dokument regulacyjny)
-- ============================================================================
CREATE TABLE IF NOT EXISTS `calibrations_AUD` (
    `REV`                   INT          NOT NULL,
    `REVTYPE`               TINYINT      NULL,
    `id`                    BIGINT       NOT NULL,
    `thermo_recorder_id`    BIGINT       NULL,
    `calibration_date`      DATE         NULL,
    `certificate_number`    VARCHAR(100) NULL,
    `valid_until`           DATE         NULL,
    `certificate_file_path` VARCHAR(500) NULL,
    PRIMARY KEY (`id`, `REV`),
    CONSTRAINT `fk_calibrations_aud_rev` FOREIGN KEY (`REV`) REFERENCES `REVINFO`(`REV`),
    INDEX `idx_cal_aud_id_rev`      (`id`, `REV` DESC),
    INDEX `idx_cal_aud_recorder`    (`thermo_recorder_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Historia wzorcowań rejestratorów — wymagane GMP';

-- ============================================================================
-- 9. calibration_points_AUD
-- ============================================================================
CREATE TABLE IF NOT EXISTS `calibration_points_AUD` (
    `REV`               INT           NOT NULL,
    `REVTYPE`           TINYINT       NULL,
    `id`                BIGINT        NOT NULL,
    `calibration_id`    BIGINT        NULL,
    `temperature_value` DECIMAL(10,4) NULL,
    `systematic_error`  DECIMAL(10,4) NULL,
    `uncertainty`       DECIMAL(10,4) NULL,
    PRIMARY KEY (`id`, `REV`),
    CONSTRAINT `fk_calibration_points_aud_rev` FOREIGN KEY (`REV`) REFERENCES `REVINFO`(`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Historia punktów wzorcowania';

-- ============================================================================
-- 10. validations_AUD  (najważniejsza — historia statusów DRAFT→COMPLETED)
-- ============================================================================
CREATE TABLE IF NOT EXISTS `validations_AUD` (
    `REV`                         INT         NOT NULL,
    `REVTYPE`                     TINYINT     NULL,
    `id`                          BIGINT      NOT NULL,
    `cooling_device_id`           BIGINT      NULL,
    `validation_plan_number`      VARCHAR(20) NULL,
    `created_date`                DATETIME(6) NULL,
    `status`                      VARCHAR(20) NULL,
    `average_device_temperature`  DOUBLE      NULL,
    `control_sensor_position`     VARCHAR(30) NULL,
    PRIMARY KEY (`id`, `REV`),
    CONSTRAINT `fk_validations_aud_rev` FOREIGN KEY (`REV`) REFERENCES `REVINFO`(`REV`),
    INDEX `idx_val_aud_id_rev`  (`id`, `REV` DESC),
    INDEX `idx_val_aud_status`  (`status`),
    INDEX `idx_val_aud_device`  (`cooling_device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Historia walidacji — kluczowa dla GMP Annex 11';

-- ============================================================================
-- DONE
-- ============================================================================
SELECT CONCAT('Envers schema v2.13.0 created at ', NOW()) AS migration_info;
