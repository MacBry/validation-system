package com.mac.bry.validationsystem.wizard.oq;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for OqTestResult entity.
 *
 * <p>
 * Manages OQ test results for wizard step 5.
 * </p>
 */
@Repository
public interface OqTestResultRepository extends JpaRepository<OqTestResult, Long> {

    /**
     * Find OQ test result for a specific draft (1-to-1)
     * @param validationDraftId Draft ID
     * @return Optional test result
     */
    Optional<OqTestResult> findByValidationDraftId(Long validationDraftId);

    /**
     * Check if a draft has OQ test results
     * @param validationDraftId Draft ID
     * @return true if results exist
     */
    boolean existsByValidationDraftId(Long validationDraftId);
}
