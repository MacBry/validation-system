package com.mac.bry.validationsystem.wizard.dto;

import com.mac.bry.validationsystem.wizard.ValidationProcedureType;
import com.mac.bry.validationsystem.wizard.WizardStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO representing the current state of a validation plan draft.
 *
 * <p>
 * Returned by the plan-review endpoint and consumed by the QA approval / rejection
 * views. Contains all identifying information (draft ID, procedure type, status,
 * current step) plus the nested plan data and the linked cooling device name.
 * </p>
 *
 * <p>
 * {@link ValidationPlanDataDto} carries the textual plan content (reason,
 * previous validation reference) — see that class for field-level documentation.
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidationPlanResponseDto {

    /**
     * Primary key of the {@link com.mac.bry.validationsystem.wizard.ValidationDraft}.
     */
    private Long id;

    /**
     * Procedure type — always PERIODIC_REVALIDATION for plan-related operations,
     * but carried here for completeness.
     */
    private ValidationProcedureType procedureType;

    /**
     * Current wizard status (e.g., IN_PROGRESS, AWAITING_QA_APPROVAL).
     */
    private WizardStatus status;

    /**
     * Current wizard step number (1–13 for PERIODIC_REVALIDATION).
     */
    private Integer currentStep;

    /**
     * Plan-phase data (reason, previous validation info).
     * Null if the draft has not yet reached step 3.
     */
    private ValidationPlanDataDto planData;

    /**
     * Display name of the cooling device selected in step 2.
     */
    private String coolingDeviceName;

    /**
     * Inventory number of the cooling device selected in step 2.
     */
    private String coolingDeviceInventoryNumber;

    /**
     * Timestamp when the draft was created (step 1).
     */
    private LocalDateTime createdAt;

    /**
     * Username of the technician who created the draft.
     */
    private String createdBy;
}
