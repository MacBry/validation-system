package com.mac.bry.validationsystem.wizard.plandata;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Value object for revalidation plan details (wizard step 3).
 *
 * <p>
 * Captures the reason for periodic revalidation and reference
 * to the previous validation of the same device.
 * </p>
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlanDetails {

    /**
     * Reason/justification for performing periodic revalidation
     */
    @Column(name = "revalidation_reason", columnDefinition = "LONGTEXT")
    private String revalidationReason;

    /**
     * Date of the previous validation for this device
     */
    @Column(name = "previous_validation_date")
    private LocalDate previousValidationDate;

    /**
     * Report number of the previous validation (e.g., "VP-2024-001")
     */
    @Column(name = "previous_validation_number", length = 100)
    private String previousValidationNumber;
}
