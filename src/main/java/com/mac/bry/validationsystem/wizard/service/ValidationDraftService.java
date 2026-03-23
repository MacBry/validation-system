package com.mac.bry.validationsystem.wizard.service;

import com.mac.bry.validationsystem.device.CoolingDevice;
import com.mac.bry.validationsystem.measurement.RecorderPosition;
import com.mac.bry.validationsystem.validation.DeviceLoadState;
import com.mac.bry.validationsystem.wizard.ValidationDraft;
import com.mac.bry.validationsystem.wizard.ValidationProcedureType;
import com.mac.bry.validationsystem.wizard.criteria.CustomAcceptanceCriterionDto;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing validation wizard drafts.
 *
 * <p>
 * Handles the multi-step wizard flow:
 * - Step 1: Select procedure type (OQ/PQ/MAPPING)
 * - Step 2: Select cooling device
 * - Step 3: Define custom acceptance criteria (locks steps 2-3)
 * - Step 4: Select device load state and recorder position
 * - Step 5: OQ tests or PQ checklist
 * - Step 6: Select measurement series
 * - Step 7-8: Review and statistics
 * - Step 9: Finalize and sign
 * </p>
 */
public interface ValidationDraftService {

    /**
     * Create a new draft for the current user
     * @param username Creator username
     * @return New draft at step 1
     */
    ValidationDraft createDraft(String username);

    /**
     * Get draft by ID and verify ownership
     * @param draftId Draft ID
     * @param username Current user
     * @return Optional draft
     */
    Optional<ValidationDraft> getDraft(Long draftId, String username);

    /**
     * Get draft without ownership check (internal use)
     * @param draftId Draft ID
     * @return Optional draft
     */
    Optional<ValidationDraft> getDraftById(Long draftId);

    /**
     * Find all active drafts for a user
     * @param username User
     * @return List of IN_PROGRESS drafts
     */
    List<ValidationDraft> findActiveDraftsForUser(String username);

    /**
     * Find all drafts for a user (including completed/abandoned)
     * @param username User
     * @return List of all drafts
     */
    List<ValidationDraft> findAllDraftsForUser(String username);

    /**
     * Save step 1: Select procedure type
     * @param draftId Draft ID
     * @param procedureType Selected procedure type
     * @return Updated draft, currentStep = 2
     */
    ValidationDraft saveStep1(Long draftId, ValidationProcedureType procedureType);

    /**
     * Save step 2: Select cooling device
     * @param draftId Draft ID
     * @param coolingDeviceId Selected device ID
     * @return Updated draft, currentStep = 3
     */
    ValidationDraft saveStep2(Long draftId, Long coolingDeviceId);

    /**
     * Save step 3: Define custom acceptance criteria
     * After this step, steps 2-3 are locked (stepLockFrom = 3)
     * @param draftId Draft ID
     * @param criteria List of custom criteria DTOs
     * @return Updated draft with criteria saved, currentStep = 4, stepLockFrom = 3
     */
    ValidationDraft saveStep3(Long draftId, List<CustomAcceptanceCriterionDto> criteria);

    /**
     * Save step 4: Select device load state and recorder position
     * @param draftId Draft ID
     * @param loadState Device load state
     * @param recorderPosition Recorder position in device
     * @return Updated draft, currentStep = 5
     */
    ValidationDraft saveStep4(Long draftId, DeviceLoadState loadState, RecorderPosition recorderPosition);

    /**
     * Navigate to a previous step (with lock checking)
     * @param draftId Draft ID
     * @param targetStep Target step to go to
     * @return Updated draft if navigation allowed
     * @throws IllegalStateException if navigation blocked by stepLockFrom
     */
    ValidationDraft navigateToStep(Long draftId, int targetStep);

    /**
     * Check if navigation back is allowed
     * @param draft The draft
     * @param targetStep Target step
     * @return true if allowed, false if blocked by stepLockFrom
     */
    boolean canNavigateBack(ValidationDraft draft, int targetStep);

    /**
     * Move draft to next step
     * @param draftId Draft ID
     * @return Updated draft with currentStep incremented
     */
    ValidationDraft moveToNextStep(Long draftId);

    /**
     * Abandon the draft (set status = ABANDONED)
     * @param draftId Draft ID
     * @return Updated draft
     */
    ValidationDraft abandonDraft(Long draftId);

    /**
     * Delete a draft completely (use sparingly)
     * @param draftId Draft ID
     */
    void deleteDraft(Long draftId);

    /**
     * Save step 6: Select measurement series
     * @param draftId Draft ID
     * @param seriesIds List of MeasurementSeries IDs to include
     * @return Updated draft with selectedSeriesIds populated, currentStep = 7
     */
    ValidationDraft saveStep6(Long draftId, List<Long> seriesIds);

    /**
     * Set step 6 selected series and validate they exist and belong to device
     * @param draftId Draft ID
     * @param seriesIds List of series IDs
     * @return Updated draft
     * @throws IllegalArgumentException if series don't belong to device or already in use
     */
    ValidationDraft validateAndSaveSeriesSelection(Long draftId, List<Long> seriesIds);
}
