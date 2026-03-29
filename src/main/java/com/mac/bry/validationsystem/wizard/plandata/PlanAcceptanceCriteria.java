package com.mac.bry.validationsystem.wizard.plandata;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Value object for planned acceptance criteria (wizard steps 5-6).
 *
 * <p>
 * Defines the temperature and uniformity thresholds that will be used
 * to evaluate the periodic revalidation measurements. These are written
 * into the validation plan PDF before measurements begin.
 * </p>
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlanAcceptanceCriteria {

    /**
     * Device load state for this revalidation (EMPTY, HALF_LOAD, FULL_LOAD)
     */
    @Column(name = "plan_device_load_state", length = 50)
    private String planDeviceLoadState;

    /**
     * Nominal temperature for the test (degrees Celsius)
     */
    @Column(name = "plan_nominal_temp")
    private Double planNominalTemp;

    /**
     * Minimum acceptable temperature (degrees Celsius)
     */
    @Column(name = "plan_acceptance_temp_min")
    private Double planAcceptanceTempMin;

    /**
     * Maximum acceptable temperature (degrees Celsius)
     */
    @Column(name = "plan_acceptance_temp_max")
    private Double planAcceptanceTempMax;

    /**
     * Maximum allowed Mean Kinetic Temperature (degrees Celsius)
     */
    @Column(name = "plan_mkt_max_temp")
    private Double planMktMaxTemp;

    /**
     * Maximum uniformity delta (temperature difference, degrees Celsius)
     */
    @Column(name = "plan_uniformity_delta_max")
    private Double planUniformityDeltaMax;

    /**
     * Maximum drift per recorder (degrees Celsius)
     */
    @Column(name = "plan_drift_max_temp")
    private Double planDriftMaxTemp;

    /**
     * Description of materials/contents stored in device (e.g., temperature-sensitive drugs)
     */
    @Column(name = "plan_material_description", columnDefinition = "TEXT")
    private String planMaterialDescription;
}
