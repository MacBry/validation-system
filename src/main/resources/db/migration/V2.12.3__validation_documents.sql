-- Sekwencer numerów dokumentów (zapewnia unikalność numeracji per typ/pracownia/rok)
CREATE TABLE document_sequence (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    type_prefix VARCHAR(10)  NOT NULL,
    lab_abbrev  VARCHAR(20)  NOT NULL,
    year        INT          NOT NULL,
    last_number INT          NOT NULL DEFAULT 0,
    CONSTRAINT uq_doc_seq UNIQUE (type_prefix, lab_abbrev, year)
);

-- Rejestr dokumentów walidacji (kontrola wersji i historii generowania)
CREATE TABLE validation_documents (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    validation_id      BIGINT       NOT NULL,
    document_number    VARCHAR(60)  NOT NULL UNIQUE,
    document_type      VARCHAR(30)  NOT NULL,
    generation_count   INT          NOT NULL DEFAULT 0,
    first_generated_at DATETIME(6),
    first_generated_by VARCHAR(100),
    last_generated_at  DATETIME(6),
    last_generated_by  VARCHAR(100),
    pdf_hash_sha256    VARCHAR(64),
    CONSTRAINT fk_vd_validation FOREIGN KEY (validation_id) REFERENCES validations(id)
);

CREATE INDEX idx_vd_validation_id ON validation_documents(validation_id);
CREATE INDEX idx_vd_type ON validation_documents(document_type);
