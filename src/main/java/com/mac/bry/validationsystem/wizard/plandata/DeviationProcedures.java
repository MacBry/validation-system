package com.mac.bry.validationsystem.wizard.plandata;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Value object for deviation/CAPA procedures (wizard step 7).
 *
 * <p>
 * Defines the corrective and preventive actions (CAPA) for each severity
 * level of deviation discovered during the revalidation measurements.
 * Written into the validation plan PDF.
 * </p>
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeviationProcedures {

    /**
     * CAPA procedure for critical deviations
     */
    @Column(name = "plan_deviation_critical_text", columnDefinition = "LONGTEXT")
    private String criticalText;

    /**
     * CAPA procedure for major deviations
     */
    @Column(name = "plan_deviation_major_text", columnDefinition = "LONGTEXT")
    private String majorText;

    /**
     * CAPA procedure for minor deviations
     */
    @Column(name = "plan_deviation_minor_text", columnDefinition = "LONGTEXT")
    private String minorText;
}
