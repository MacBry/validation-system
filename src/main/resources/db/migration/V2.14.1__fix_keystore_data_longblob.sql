-- Fix: keystore_data kolumna byla za mala (TINYBLOB/BLOB) zamiast LONGBLOB.
-- JPA @Lob na byte[] bez columnDefinition moze tworzyc TINYBLOB w MySQL.
-- Wymuszone LONGBLOB (do 4 GB) — wystarczy na dowolny plik .p12/.jks.

ALTER TABLE company_certificates
    MODIFY COLUMN keystore_data LONGBLOB NOT NULL;
