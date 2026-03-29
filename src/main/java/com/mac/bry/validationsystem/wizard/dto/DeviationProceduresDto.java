package com.mac.bry.validationsystem.wizard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for deviation (CAPA) procedures in PERIODIC_REVALIDATION wizard step 7.
 *
 * <p>
 * Contains the corrective and preventive action (CAPA) descriptions for three
 * severity levels of deviations that may occur during the measurement phase.
 * These are required by GMP Annex 15 to be defined before measurements begin.
 * </p>
 *
 * Mapped to {@code validation_plan_data} columns:
 * plan_deviation_critical_text, plan_deviation_major_text, plan_deviation_minor_text
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviationProceduresDto {

    /**
     * CAPA dla odchylen krytycznych.
     * Describes the procedure when a critical deviation is detected
     * (e.g., temperature excursion outside acceptance range).
     */
    @NotBlank(message = "Procedura dla odchylen krytycznych jest wymagana")
    @Size(min = 10, max = 5000, message = "Procedura musi miec od 10 do 5000 znakow")
    private String planDeviationCriticalText;

    /**
     * CAPA dla odchylen powaznych.
     * Describes the procedure when a major deviation is detected
     * (e.g., MKT threshold exceeded).
     */
    @NotBlank(message = "Procedura dla odchylen powaznych jest wymagana")
    @Size(min = 10, max = 5000, message = "Procedura musi miec od 10 do 5000 znakow")
    private String planDeviationMajorText;

    /**
     * CAPA dla odchylen mniejszych.
     * Describes the procedure when a minor deviation is detected
     * (e.g., single data point outside range but within tolerance).
     */
    @NotBlank(message = "Procedura dla odchylen mniejszych jest wymagana")
    @Size(min = 10, max = 5000, message = "Procedura musi miec od 10 do 5000 znakow")
    private String planDeviationMinorText;

    /**
     * Checks whether all three deviation procedure texts are complete.
     */
    public boolean isComplete() {
        return isNotBlank(planDeviationCriticalText)
            && isNotBlank(planDeviationMajorText)
            && isNotBlank(planDeviationMinorText);
    }

    private boolean isNotBlank(String text) {
        return text != null && !text.isBlank() && text.length() >= 10;
    }
}
