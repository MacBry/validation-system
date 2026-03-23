package com.mac.bry.validationsystem.wizard;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ValidationDraft entity.
 *
 * <p>
 * Manages persistent wizard sessions.
 * </p>
 */
@Repository
public interface ValidationDraftRepository extends JpaRepository<ValidationDraft, Long> {

    /**
     * Find all active drafts created by a specific user
     * @param createdBy Username
     * @param status Wizard status (e.g., IN_PROGRESS)
     * @return List of matching drafts
     */
    List<ValidationDraft> findByCreatedByAndStatus(String createdBy, WizardStatus status);

    /**
     * Find all active drafts created by a specific user (ignoring status)
     * @param createdBy Username
     * @return List of drafts for this user
     */
    List<ValidationDraft> findByCreatedBy(String createdBy);

    /**
     * Find all drafts for a specific cooling device
     * @param coolingDeviceId Device ID
     * @return List of drafts for this device
     */
    List<ValidationDraft> findByCoolingDeviceIdAndStatus(Long coolingDeviceId, WizardStatus status);

    /**
     * Find a draft by ID and verify it belongs to the given user (authorization check)
     * @param id Draft ID
     * @param createdBy Username
     * @return Optional draft (empty if not found or doesn't belong to user)
     */
    Optional<ValidationDraft> findByIdAndCreatedBy(Long id, String createdBy);

    /**
     * Find a completed draft that created a specific validation
     * @param validationId Validation ID
     * @return Optional draft
     */
    Optional<ValidationDraft> findByCompletedValidationId(Long validationId);
}
