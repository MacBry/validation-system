package com.mac.bry.validationsystem.wizard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO for saving plan details in PERIODIC_REVALIDATION wizard step 3.
 *
 * <p>
 * Contains the revalidation justification, reference to the previous validation,
 * and the date of the previous validation. These fields form the "Plan Details"
 * section of the validation plan document (GMP Annex 15 §10).
 * </p>
 *
 * Mapped to {@code validation_plan_data} columns:
 * revalidation_reason, previous_validation_date, previous_validation_number
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidationPlanDataDto {

    /**
     * Uzasadnienie podjecia rewalidacji okresowej.
     * Required field - must explain why periodic revalidation is being initiated.
     */
    @NotBlank(message = "Uzasadnienie rewalidacji jest wymagane")
    @Size(min = 10, max = 5000, message = "Uzasadnienie musi miec od 10 do 5000 znakow")
    private String revalidationReason;

    /**
     * Data poprzedniej walidacji tego urzadzenia.
     * Must be in the past or today.
     */
    @PastOrPresent(message = "Data poprzedniej walidacji nie moze byc w przyszlosci")
    private LocalDate previousValidationDate;

    /**
     * Numer sprawozdania z poprzedniej walidacji (e.g., "VP-2024-001").
     */
    @Size(max = 100, message = "Numer sprawozdania moze miec maksymalnie 100 znakow")
    private String previousValidationNumber;

    /**
     * Checks whether all required fields are present and valid.
     */
    public boolean isComplete() {
        return revalidationReason != null && !revalidationReason.isBlank()
            && revalidationReason.length() >= 10;
    }
}
