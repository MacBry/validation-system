package com.mac.bry.validationsystem.validation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ValidationSignatureRepository extends JpaRepository<ValidationSignature, Long> {

    Optional<ValidationSignature> findByValidationId(Long validationId);
}
