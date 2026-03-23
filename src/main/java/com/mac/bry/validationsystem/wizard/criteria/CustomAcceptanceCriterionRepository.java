package com.mac.bry.validationsystem.wizard.criteria;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for CustomAcceptanceCriterion entity.
 *
 * <p>
 * Manages custom acceptance criteria for wizard step 3.
 * </p>
 */
@Repository
public interface CustomAcceptanceCriterionRepository extends JpaRepository<CustomAcceptanceCriterion, Long> {

    /**
     * Find all custom criteria for a specific draft, ordered by displayOrder
     * @param validationDraftId Draft ID
     * @return List of criteria ordered by display_order
     */
    List<CustomAcceptanceCriterion> findByValidationDraftIdOrderByDisplayOrder(Long validationDraftId);

    /**
     * Delete all criteria for a draft (cascade on FK delete should handle this)
     * @param validationDraftId Draft ID
     */
    void deleteByValidationDraftId(Long validationDraftId);

    /**
     * Count criteria for a draft
     * @param validationDraftId Draft ID
     * @return Number of criteria
     */
    long countByValidationDraftId(Long validationDraftId);
}
