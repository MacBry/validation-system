package com.mac.bry.validationsystem.wizard.service;

import com.mac.bry.validationsystem.wizard.ValidationDraft;
import com.mac.bry.validationsystem.wizard.ValidationPlanData;
import com.mac.bry.validationsystem.wizard.dto.DeviationProceduresDto;
import com.mac.bry.validationsystem.wizard.dto.MappingStatusDto;
import com.mac.bry.validationsystem.wizard.dto.PlanCriteriaDto;
import com.mac.bry.validationsystem.wizard.dto.ValidationPlanDataDto;

import java.util.Optional;

/**
 * Service for managing validation plan data (PERIODIC_REVALIDATION only).
 *
 * Handles the planning phase (steps 1-8) of PERIODIC_REVALIDATION procedure type,
 * including creation, modification, and finalization of validation plans.
 */
public interface ValidationPlanDataService {

    /**
     * Initialize or retrieve existing plan data for a draft.
     *
     * @param draftId ID of the ValidationDraft
     * @return Existing or newly created ValidationPlanData
     */
    ValidationPlanData initializePlanData(Long draftId);

    /**
     * Find plan data by ID.
     *
     * @param planDataId ID of the ValidationPlanData
     * @return Optional containing the plan data if found
     */
    Optional<ValidationPlanData> findById(Long planDataId);

    /**
     * Find plan data associated with a draft.
     *
     * @param draftId ID of the ValidationDraft
     * @return Optional containing the plan data if associated
     */
    Optional<ValidationPlanData> findByDraftId(Long draftId);

    /**
     * Save plan details from step 3 (reason, previous validation reference).
     *
     * @param draftId ID of the ValidationDraft
     * @param dto Plan details DTO
     * @return Updated ValidationDraft
     */
    ValidationDraft savePlanDetails(Long draftId, ValidationPlanDataDto dto);

    /**
     * Save mapping status information from step 4.
     *
     * @param draftId ID of the ValidationDraft
     * @param mappingStatus Mapping status DTO
     * @return Updated ValidationDraft
     */
    ValidationDraft saveMappingStatus(Long draftId, MappingStatusDto mappingStatus);

    /**
     * Save acceptance criteria from steps 5-6.
     *
     * @param draftId ID of the ValidationDraft
     * @param criteria Plan criteria DTO
     * @return Updated ValidationDraft
     */
    ValidationDraft savePlanCriteria(Long draftId, PlanCriteriaDto criteria);

    /**
     * Save deviation procedures from step 7.
     *
     * @param draftId ID of the ValidationDraft
     * @param procedures Deviation procedures DTO
     * @return Updated ValidationDraft
     */
    ValidationDraft saveDeviationProcedures(Long draftId, DeviationProceduresDto procedures);

    /**
     * Finalize plan with technik signature (step 8).
     * Transitions draft to AWAITING_QA_APPROVAL status.
     *
     * @param draftId ID of the ValidationDraft
     * @param username Technik username
     * @param rawPassword Raw password for signature verification
     * @return Updated ValidationDraft with AWAITING_QA_APPROVAL status
     */
    ValidationDraft signPlanAsTechnician(Long draftId, String username, String rawPassword);

    /**
     * Approve plan as QA (electronic signature path).
     * Transitions draft back to IN_PROGRESS at step 9.
     *
     * @param draftId ID of the ValidationDraft
     * @param qaUsername QA username
     * @param rawPassword Raw password for signature verification
     * @return Updated ValidationDraft with IN_PROGRESS status
     */
    ValidationDraft approvePlanAsQa(Long draftId, String qaUsername, String rawPassword);

    /**
     * Approve plan via manual signature scan (external QA path).
     * Transitions draft to IN_PROGRESS at step 9.
     *
     * @param draftId ID of the ValidationDraft
     * @param technicianUsername Technician username who uploads the scan
     * @param scan Scanned PDF document
     * @return Updated ValidationDraft with IN_PROGRESS status
     */
    ValidationDraft approvePlanExternal(Long draftId, String technicianUsername, org.springframework.web.multipart.MultipartFile scan);

    /**
     * Reject plan as QA with reason.
     * Returns draft to step 8 for revision.
     *
     * @param draftId ID of the ValidationDraft
     * @param qaUsername QA username
     * @param rejectionReason Reason for rejection
     * @return Updated ValidationDraft
     */
    ValidationDraft rejectPlan(Long draftId, String qaUsername, String rejectionReason);

    /**
     * Save plan data (generic persistence method).
     *
     * @param planData Plan data entity
     * @return Saved plan data
     */
    ValidationPlanData save(ValidationPlanData planData);
}
