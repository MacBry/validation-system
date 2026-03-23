CREATE TABLE validation_signatures (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY,
    validation_id   BIGINT       NOT NULL,
    signed_by       VARCHAR(100) NOT NULL,
    signed_at       DATETIME(6)  NOT NULL,
    signing_intent  TEXT         NOT NULL,
    cert_subject    VARCHAR(255),
    cert_serial     VARCHAR(100),
    document_hash   VARCHAR(64),
    signed_pdf_path VARCHAR(500),
    CONSTRAINT fk_sig_validation FOREIGN KEY (validation_id) REFERENCES validations(id),
    CONSTRAINT uq_sig_validation  UNIQUE (validation_id)
);
