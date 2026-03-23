-- Dodanie wartości COMPLETED do kolumny status w tabeli validations
-- Wymagane do elektronicznego podpisywania walidacji (Annex 11 §12)
ALTER TABLE validations
    MODIFY COLUMN status ENUM('DRAFT','APPROVED','REJECTED','COMPLETED') NOT NULL;
