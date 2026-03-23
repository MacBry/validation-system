-- ============================================================================
-- VALIDATION SYSTEM v2.13.3 - FIX ENVERS REVINFO COLUMNS
-- ============================================================================
-- Hibernate Envers DefaultTrackingModifiedEntitiesRevisionEntity domyślnie 
-- mapuje kolumny na 'id' i 'timestamp'. W V2.13.0 utworzyliśmy 'REV' 
-- i 'REVTSTMP', co powoduje błąd "Field 'id' doesn't have a default value"
-- przy logowaniu (zapisie do bazy). Zmiana nazw kolumn pod Envers.
-- ============================================================================

-- Usuwamy najpierw klucze obce z innych tabel AUD używające kolumny REV (zanim ją zmienimy na id)
ALTER TABLE companies_AUD DROP FOREIGN KEY fk_companies_aud_rev;
ALTER TABLE departments_AUD DROP FOREIGN KEY fk_departments_aud_rev;
ALTER TABLE laboratories_AUD DROP FOREIGN KEY fk_laboratories_aud_rev;
ALTER TABLE material_types_AUD DROP FOREIGN KEY fk_material_types_aud_rev;
ALTER TABLE cooling_devices_AUD DROP FOREIGN KEY fk_cooling_devices_aud_rev;
ALTER TABLE thermo_recorders_AUD DROP FOREIGN KEY fk_thermo_recorders_aud_rev;
ALTER TABLE calibrations_AUD DROP FOREIGN KEY fk_calibrations_aud_rev;
ALTER TABLE calibration_points_AUD DROP FOREIGN KEY fk_calibration_points_aud_rev;
ALTER TABLE validations_AUD DROP FOREIGN KEY fk_validations_aud_rev;

ALTER TABLE users_AUD DROP FOREIGN KEY fk_users_aud_rev;
ALTER TABLE user_permissions_AUD DROP FOREIGN KEY fk_user_permissions_aud_rev;
ALTER TABLE validation_signatures_AUD DROP FOREIGN KEY fk_validation_signatures_aud_rev;
ALTER TABLE validation_documents_AUD DROP FOREIGN KEY fk_validation_documents_aud_rev;
ALTER TABLE validation_plan_numbers_AUD DROP FOREIGN KEY fk_vpn_aud_rev;
ALTER TABLE revchanges DROP FOREIGN KEY fk_revchanges_revinfo;

-- Zmieniamy nazwy kolumn w tabeli głównej REVINFO
ALTER TABLE REVINFO CHANGE REV id INT NOT NULL AUTO_INCREMENT;
ALTER TABLE REVINFO CHANGE REVTSTMP timestamp BIGINT NOT NULL COMMENT 'Timestamp Unix (ms)';

-- Przywracamy klucze obce poprawnie ustawione na 'id' (chociaż i tak Hibernate szuka id)
ALTER TABLE companies_AUD ADD CONSTRAINT fk_companies_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(id);
ALTER TABLE departments_AUD ADD CONSTRAINT fk_departments_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(id);
ALTER TABLE laboratories_AUD ADD CONSTRAINT fk_laboratories_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(id);
ALTER TABLE material_types_AUD ADD CONSTRAINT fk_material_types_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(id);
ALTER TABLE cooling_devices_AUD ADD CONSTRAINT fk_cooling_devices_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(id);
ALTER TABLE thermo_recorders_AUD ADD CONSTRAINT fk_thermo_recorders_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(id);
ALTER TABLE calibrations_AUD ADD CONSTRAINT fk_calibrations_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(id);
ALTER TABLE calibration_points_AUD ADD CONSTRAINT fk_calibration_points_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(id);
ALTER TABLE validations_AUD ADD CONSTRAINT fk_validations_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(id);

ALTER TABLE users_AUD ADD CONSTRAINT fk_users_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(id);
ALTER TABLE user_permissions_AUD ADD CONSTRAINT fk_user_permissions_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(id);
ALTER TABLE validation_signatures_AUD ADD CONSTRAINT fk_validation_signatures_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(id);
ALTER TABLE validation_documents_AUD ADD CONSTRAINT fk_validation_documents_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(id);
ALTER TABLE validation_plan_numbers_AUD ADD CONSTRAINT fk_vpn_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(id);
ALTER TABLE revchanges ADD CONSTRAINT fk_revchanges_revinfo FOREIGN KEY (REV) REFERENCES REVINFO(id);
