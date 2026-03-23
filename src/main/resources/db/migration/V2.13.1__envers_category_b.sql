-- ============================================================================
-- VALIDATION SYSTEM v2.13.1 - HIBERNATE ENVERS - KATEGORIA B
-- ============================================================================
-- Tabele AUD dla encji bezpieczeństwa i dokumentów walidacji
-- Wcześniej pominiętych w V2.13.0, teraz objętych audytem Envers.
-- GMP Annex 11 §10, §12 / 21 CFR Part 11
-- ============================================================================

SET NAMES utf8mb4;

-- ============================================================================
-- 1. users_AUD
--    Kluczowe: kiedy zmieniono hasło, zablokowano konto, kto modyfikował
--    @NotAudited: roles (ManyToMany → Role nie jest @Audited), permissions_cache_json (pochodna)
-- ============================================================================
CREATE TABLE IF NOT EXISTS `users_AUD` (
    `REV`                    INT          NOT NULL,
    `REVTYPE`                TINYINT      NULL     COMMENT '0=ADD 1=MOD 2=DEL',
    `id`                     BIGINT       NOT NULL,
    `username`               VARCHAR(50)  NULL,
    `email`                  VARCHAR(100) NULL,
    `password`               VARCHAR(255) NULL     COMMENT 'BCrypt hash — rejestruje moment zmiany hasła',
    `enabled`                BIT(1)       NULL,
    `locked`                 BIT(1)       NULL,
    `account_expired`        BIT(1)       NULL,
    `credentials_expired`    BIT(1)       NULL,
    `failed_login_attempts`  INT          NULL,
    `locked_until`           DATETIME(6)  NULL,
    `first_name`             VARCHAR(100) NULL,
    `last_name`              VARCHAR(100) NULL,
    `phone`                  VARCHAR(20)  NULL,
    `created_date`           DATETIME(6)  NULL,
    `updated_date`           DATETIME(6)  NULL,
    `last_login`             DATETIME(6)  NULL,
    `created_by`             BIGINT       NULL,
    PRIMARY KEY (`id`, `REV`),
    CONSTRAINT `fk_users_aud_rev` FOREIGN KEY (`REV`) REFERENCES `REVINFO`(`REV`),
    INDEX `idx_users_aud_id_rev`   (`id`, `REV` DESC),
    INDEX `idx_users_aud_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Historia kont użytkowników — GMP Annex 11 §10';

-- ============================================================================
-- 2. user_permissions_AUD
--    Kluczowe: kto, kiedy i na jaką firmę/dział/pracownię nadał uprawnienia
-- ============================================================================
CREATE TABLE IF NOT EXISTS `user_permissions_AUD` (
    `REV`              INT          NOT NULL,
    `REVTYPE`          TINYINT      NULL,
    `id`               BIGINT       NOT NULL,
    `user_id`          BIGINT       NULL,
    `company_id`       BIGINT       NULL,
    `permission_type`  VARCHAR(50)  NULL,
    `department_id`    BIGINT       NULL,
    `laboratory_id`    BIGINT       NULL,
    `granted_by`       BIGINT       NULL,
    `granted_date`     DATETIME(6)  NULL,
    `expires_date`     DATETIME(6)  NULL,
    PRIMARY KEY (`id`, `REV`),
    CONSTRAINT `fk_user_permissions_aud_rev` FOREIGN KEY (`REV`) REFERENCES `REVINFO`(`REV`),
    INDEX `idx_up_aud_user`    (`user_id`),
    INDEX `idx_up_aud_company` (`company_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Historia uprawnień użytkowników — GMP Annex 11 §10';

-- ============================================================================
-- 3. validation_signatures_AUD
--    Kluczowe: rejestr podpisów elektronicznych wg Annex 11 §12
--    Encja jest append-only (jeden podpis na walidację), ale DEL jest możliwy
-- ============================================================================
CREATE TABLE IF NOT EXISTS `validation_signatures_AUD` (
    `REV`            INT          NOT NULL,
    `REVTYPE`        TINYINT      NULL,
    `id`             BIGINT       NOT NULL,
    `signed_by`      VARCHAR(100) NULL,
    `signed_at`      DATETIME(6)  NULL,
    `signing_intent` TEXT         NULL,
    `cert_subject`   VARCHAR(255) NULL,
    `cert_serial`    VARCHAR(100) NULL,
    `document_hash`  VARCHAR(64)  NULL,
    `signed_pdf_path` VARCHAR(500) NULL,
    PRIMARY KEY (`id`, `REV`),
    CONSTRAINT `fk_validation_signatures_aud_rev` FOREIGN KEY (`REV`) REFERENCES `REVINFO`(`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Historia podpisów elektronicznych — Annex 11 §12';

-- ============================================================================
-- 4. validation_documents_AUD
--    Kluczowe: kto i kiedy generował dokumenty, ile razy, hash zmian
-- ============================================================================
CREATE TABLE IF NOT EXISTS `validation_documents_AUD` (
    `REV`                  INT          NOT NULL,
    `REVTYPE`              TINYINT      NULL,
    `id`                   BIGINT       NOT NULL,
    `validation_id`        BIGINT       NULL,
    `document_number`      VARCHAR(60)  NULL,
    `document_type`        VARCHAR(30)  NULL,
    `generation_count`     INT          NULL,
    `first_generated_at`   DATETIME(6)  NULL,
    `first_generated_by`   VARCHAR(100) NULL,
    `last_generated_at`    DATETIME(6)  NULL,
    `last_generated_by`    VARCHAR(100) NULL,
    `pdf_hash_sha256`      VARCHAR(64)  NULL,
    `data_changed`         BIT(1)       NULL,
    PRIMARY KEY (`id`, `REV`),
    CONSTRAINT `fk_validation_documents_aud_rev` FOREIGN KEY (`REV`) REFERENCES `REVINFO`(`REV`),
    INDEX `idx_vd_aud_validation` (`validation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Historia dokumentów walidacji';

-- ============================================================================
-- 5. validation_plan_numbers_AUD
--    Kluczowe: kiedy zmieniono numer RPW urządzenia i kto to zrobił
-- ============================================================================
CREATE TABLE IF NOT EXISTS `validation_plan_numbers_AUD` (
    `REV`               INT    NOT NULL,
    `REVTYPE`           TINYINT NULL,
    `id`                BIGINT NOT NULL,
    `year`              INT    NULL,
    `plan_number`       INT    NULL,
    `cooling_device_id` BIGINT NULL,
    PRIMARY KEY (`id`, `REV`),
    CONSTRAINT `fk_vpn_aud_rev` FOREIGN KEY (`REV`) REFERENCES `REVINFO`(`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Historia numerów RPW urządzeń';

-- ============================================================================
-- DONE
-- ============================================================================
SELECT CONCAT('Envers category B schema v2.13.1 created at ', NOW()) AS migration_info;
