package com.mac.bry.validationsystem.validation;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, Long> {

    /**
     * Pobiera sekwencer z blokadą pesymistyczną, zapobiegając równoległej modyfikacji.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DocumentSequence> findByTypePrefixAndLabAbbrevAndYear(
            String typePrefix, String labAbbrev, Integer year);
}
