package com.mac.bry.validationsystem.wizard.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for acceptance criteria and load state in PERIODIC_REVALIDATION wizard steps 5-6.
 *
 * <p>
 * Contains the device load state for this revalidation run and the acceptance criteria
 * (temperature range, MKT threshold, uniformity delta, drift max) that define pass/fail
 * boundaries for the measurement phase.
 * </p>
 *
 * Mapped to {@code validation_plan_data} columns:
 * plan_device_load_state, plan_nominal_temp, plan_acceptance_temp_min,
 * plan_acceptance_temp_max, plan_mkt_max_temp, plan_uniformity_delta_max, plan_drift_max_temp
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanCriteriaDto {

    /**
     * Stan obciazenia urzadzenia (EMPTY, HALF_LOAD, FULL_LOAD).
     */
    @NotBlank(message = "Stan obciazenia urzadzenia jest wymagany")
    private String planDeviceLoadState;

    /**
     * Temperatura nominalna dla tego testu (C).
     * Typically the target set-point temperature of the device.
     */
    @NotNull(message = "Temperatura nominalna jest wymagana")
    @DecimalMin(value = "-80.0", message = "Temperatura nominalna nie moze byc nizsza niz -80°C")
    @DecimalMax(value = "80.0", message = "Temperatura nominalna nie moze byc wyzsza niz 80°C")
    private Double planNominalTemp;

    /**
     * Minimalna temperatura akceptacji (C).
     * Lower bound of the acceptable temperature range.
     */
    @NotNull(message = "Minimalna temperatura akceptacji jest wymagana")
    @DecimalMin(value = "-80.0", message = "Minimalna temperatura nie moze byc nizsza niz -80°C")
    @DecimalMax(value = "80.0", message = "Minimalna temperatura nie moze byc wyzsza niz 80°C")
    private Double planAcceptanceTempMin;

    /**
     * Maksymalna temperatura akceptacji (C).
     * Upper bound of the acceptable temperature range.
     */
    @NotNull(message = "Maksymalna temperatura akceptacji jest wymagana")
    @DecimalMin(value = "-80.0", message = "Maksymalna temperatura nie moze byc nizsza niz -80°C")
    @DecimalMax(value = "80.0", message = "Maksymalna temperatura nie moze byc wyzsza niz 80°C")
    private Double planAcceptanceTempMax;

    /**
     * Maksymalny MKT dopuszczony (C).
     * Mean Kinetic Temperature upper limit.
     */
    @DecimalMin(value = "-80.0", message = "MKT nie moze byc nizszy niz -80°C")
    @DecimalMax(value = "80.0", message = "MKT nie moze byc wyzszy niz 80°C")
    private Double planMktMaxTemp;

    /**
     * Maksymalny dryft jednorodnosci (delta T max, C).
     * Maximum temperature difference between any two sensors at the same time point.
     */
    @DecimalMin(value = "0.0", message = "Dryft jednorodnosci nie moze byc ujemny")
    @DecimalMax(value = "20.0", message = "Dryft jednorodnosci nie moze przekraczac 20°C")
    private Double planUniformityDeltaMax;

    /**
     * Maksymalny dryft na rejestrator (C).
     * Maximum temperature drift for a single recorder over the measurement period.
     */
    @DecimalMin(value = "0.0", message = "Dryft na rejestrator nie moze byc ujemny")
    @DecimalMax(value = "20.0", message = "Dryft na rejestrator nie moze przekraczac 20°C")
    private Double planDriftMaxTemp;

    /**
     * Validates that the temperature range is logically consistent (min < max).
     */
    public boolean isTemperatureRangeValid() {
        if (planAcceptanceTempMin == null || planAcceptanceTempMax == null) {
            return false;
        }
        return planAcceptanceTempMin < planAcceptanceTempMax;
    }

    /**
     * Checks whether all required criteria fields are populated.
     */
    public boolean isComplete() {
        return planDeviceLoadState != null && !planDeviceLoadState.isBlank()
            && planNominalTemp != null
            && planAcceptanceTempMin != null
            && planAcceptanceTempMax != null
            && isTemperatureRangeValid();
    }
}
