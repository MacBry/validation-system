-- ============================================================================
-- VALIDATION SYSTEM v2.13.2 - ENVERS REVCHANGES TABLE
-- ============================================================================
-- Tabela wymagana przez DefaultTrackingModifiedEntitiesRevisionEntity
-- Wyłączamy Hibernate ddl-auto dla tej złączeniowej tabeli, definiując ją wprost
-- ============================================================================

CREATE TABLE IF NOT EXISTS `REVCHANGES` (
    `REV` INT NOT NULL,
    `ENTITYNAME` VARCHAR(255) NULL,
    CONSTRAINT `fk_revchanges_revinfo` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`),
    INDEX `idx_revchanges_rev` (`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Przechowuje nazwy encji zmienionych w danej rewizji (Envers)';
