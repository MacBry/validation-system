package com.mac.bry.validationsystem.wizard.criteria;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for creating/updating custom acceptance criteria in wizard step 3.
 *
 * <p>
 * Used in API requests from UI form, converted to CustomAcceptanceCriterion entity.
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomAcceptanceCriterionDto {

    /**
     * Field to evaluate (MIN_TEMP, MAX_TEMP, AVG_TEMP, MKT, STD_DEV, etc.)
     */
    private AcceptanceCriterionField fieldName;

    /**
     * Comparison operator (GT, LT, GTE, LTE, EQ)
     */
    private CriterionOperator operator;

    /**
     * Limit value (e.g., 20.0 for "MKT ≤ 20.0°C")
     */
    private Double limitValue;

    /**
     * Unit of measurement (auto-populated from fieldName if null)
     */
    private String unit;

    /**
     * Is this a standard GMP criterion
     */
    private Boolean isStandard;

    /**
     * Display name for UI
     */
    public String getDisplayString() {
        if (fieldName != null && operator != null && limitValue != null) {
            String unit = this.unit != null ? this.unit : fieldName.getUnit();
            return operator.generateDisplayString(fieldName.getDisplayName(), limitValue, unit);
        }
        return "Niepełne kryterium";
    }

    /**
     * Validates that all required fields are present
     */
    public boolean isValid() {
        return fieldName != null && operator != null && limitValue != null;
    }
}
