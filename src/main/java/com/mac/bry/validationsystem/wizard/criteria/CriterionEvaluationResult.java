package com.mac.bry.validationsystem.wizard.criteria;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of evaluating a single acceptance criterion.
 *
 * <p>
 * Contains both the criterion definition and its evaluation result.
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CriterionEvaluationResult {

    /**
     * Field name being evaluated
     */
    private String fieldName;

    /**
     * The operator used (GT, LT, GTE, LTE, EQ)
     */
    private CriterionOperator operator;

    /**
     * Limit value from the criterion
     */
    private Double limitValue;

    /**
     * Actual measured value from statistics
     */
    private Double actualValue;

    /**
     * Unit of measurement
     */
    private String unit;

    /**
     * Whether the criterion was passed
     */
    private Boolean passed;

    /**
     * Human-readable message explaining the result
     */
    private String message;

    /**
     * CSS class for status badge styling
     */
    public String getStatusCssClass() {
        if (passed == null) {
            return "badge-secondary";
        }
        return passed ? "badge-success" : "badge-danger";
    }

    /**
     * Status badge text
     */
    public String getStatusBadge() {
        if (passed == null) {
            return "Nie ocenione";
        }
        return passed ? "✅ Spełnione" : "❌ Niespełnione";
    }
}
