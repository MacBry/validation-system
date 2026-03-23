package com.mac.bry.validationsystem.wizard.pq;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for PqChecklistItem entity.
 *
 * <p>
 * Manages PQ checklist items for wizard step 5.
 * </p>
 */
@Repository
public interface PqChecklistItemRepository extends JpaRepository<PqChecklistItem, Long> {

    /**
     * Find all PQ checklist items for a specific draft, ordered by displayOrder
     * @param validationDraftId Draft ID
     * @return List of items ordered by display_order
     */
    List<PqChecklistItem> findByValidationDraftIdOrderByDisplayOrder(Long validationDraftId);

    /**
     * Find a specific PQ item by code (e.g., "PQ-01") within a draft
     * @param validationDraftId Draft ID
     * @param itemCode Item code (e.g., "PQ-01")
     * @return Optional item
     */
    Optional<PqChecklistItem> findByValidationDraftIdAndItemCode(Long validationDraftId, String itemCode);

    /**
     * Delete all items for a draft (cascade on FK delete should handle this)
     * @param validationDraftId Draft ID
     */
    void deleteByValidationDraftId(Long validationDraftId);

    /**
     * Count items for a draft
     * @param validationDraftId Draft ID
     * @return Number of items
     */
    long countByValidationDraftId(Long validationDraftId);

    /**
     * Count passed items for a draft
     * @param validationDraftId Draft ID
     * @return Number of items where passed = true
     */
    long countByValidationDraftIdAndPassedTrue(Long validationDraftId);

    /**
     * Check if a draft has all items assessed
     * @param validationDraftId Draft ID
     * @return true if all items are assessed (passed != null)
     */
    boolean existsByValidationDraftIdAndPassedIsNull(Long validationDraftId);
}
