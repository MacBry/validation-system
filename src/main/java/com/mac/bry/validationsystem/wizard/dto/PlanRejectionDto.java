package com.mac.bry.validationsystem.wizard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for QA rejection of the validation plan.
 *
 * <p>
 * Submitted by a QA user when the plan does not meet requirements.
 * The rejection reason is recorded in
 * {@link com.mac.bry.validationsystem.wizard.plandata.RejectionAuditTrail}
 * and displayed to the technician as "Komentarz QA do poprawy".
 * </p>
 *
 * <p>
 * After rejection the wizard status transitions back to IN_PROGRESS so
 * the technician can revise and re-submit the plan for QA approval.
 * Each rejection increments the rejection count for audit purposes.
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanRejectionDto {

    /**
     * Reason for rejecting the validation plan.
     * Must clearly describe what needs to be corrected before re-submission.
     * Minimum 10 characters ensures a meaningful explanation is provided.
     */
    @NotBlank(message = "Uzasadnienie odrzucenia jest wymagane")
    @Size(
        min = 10,
        max = 1000,
        message = "Uzasadnienie odrzucenia musi miec od 10 do 1000 znakow"
    )
    private String rejectionReason;
}
