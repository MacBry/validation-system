-- Flaga wykrywania zmian danych między kolejnymi generacjami tego samego dokumentu
ALTER TABLE validation_documents
    ADD COLUMN data_changed BOOLEAN NOT NULL DEFAULT FALSE;
