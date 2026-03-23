package com.mac.bry.validationsystem.validation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ValidationDocumentRepository extends JpaRepository<ValidationDocument, Long> {

    Optional<ValidationDocument> findByValidationIdAndDocumentType(Long validationId, DocumentType documentType);

    List<ValidationDocument> findByValidationIdOrderByDocumentTypeAsc(Long validationId);
}
