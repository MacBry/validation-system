CREATE TABLE company_certificates (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id        BIGINT        NOT NULL,
    alias             VARCHAR(100),
    subject           VARCHAR(500),
    issuer            VARCHAR(500),
    serial_number     VARCHAR(100),
    valid_from        DATETIME,
    valid_to          DATETIME,
    sha256_fingerprint VARCHAR(130),
    keystore_data     LONGBLOB      NOT NULL,
    keystore_password VARCHAR(255)  NOT NULL,
    active            BOOLEAN       NOT NULL DEFAULT TRUE,
    uploaded_by       BIGINT,
    uploaded_at       DATETIME      NOT NULL,
    CONSTRAINT fk_cert_company FOREIGN KEY (company_id) REFERENCES companies(id)
) ENGINE=InnoDB;

CREATE INDEX idx_cert_company_active ON company_certificates(company_id, active);

-- Envers (Kategoria A) — bez kolumn keystore_data i keystore_password (@NotAudited)
CREATE TABLE company_certificates_AUD (
    id                BIGINT    NOT NULL,
    REV               INT       NOT NULL,
    REVTYPE           TINYINT,
    company_id        BIGINT,
    alias             VARCHAR(100),
    subject           VARCHAR(500),
    issuer            VARCHAR(500),
    serial_number     VARCHAR(100),
    valid_from        DATETIME,
    valid_to          DATETIME,
    sha256_fingerprint VARCHAR(130),
    active            BOOLEAN,
    uploaded_by       BIGINT,
    uploaded_at       DATETIME,
    PRIMARY KEY (id, REV),
    CONSTRAINT fk_cert_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(REV)
) ENGINE=InnoDB;
