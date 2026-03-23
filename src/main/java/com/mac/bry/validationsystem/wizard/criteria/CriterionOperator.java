package com.mac.bry.validationsystem.wizard.criteria;

import java.util.function.BiFunction;

/**
 * Operatory do porównania w kryteriach akceptacji.
 *
 * <p>
 * Defines logical operators for acceptance criteria evaluation.
 * Each operator has a display name, symbol, and evaluation function.
 * </p>
 */
public enum CriterionOperator {

    /**
     * Greater than (>)
     */
    GT("Większe niż", ">", (actual, limit) -> actual > limit),

    /**
     * Less than (<)
     */
    LT("Mniejsze niż", "<", (actual, limit) -> actual < limit),

    /**
     * Greater than or equal to (>=)
     */
    GTE("Większe lub równe", "≥", (actual, limit) -> actual >= limit),

    /**
     * Less than or equal to (<=)
     */
    LTE("Mniejsze lub równe", "≤", (actual, limit) -> actual <= limit),

    /**
     * Equal to (=)
     */
    EQ("Równe", "=", (actual, limit) -> Math.abs(actual - limit) < 0.0001);

    private final String displayName;
    private final String symbol;
    private final BiFunction<Double, Double, Boolean> evaluator;

    CriterionOperator(String displayName, String symbol, BiFunction<Double, Double, Boolean> evaluator) {
        this.displayName = displayName;
        this.symbol = symbol;
        this.evaluator = evaluator;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSymbol() {
        return symbol;
    }

    /**
     * Evaluates actual value against limit using this operator
     *
     * @param actual    The actual measured value
     * @param limit     The limit threshold
     * @return true if condition is met, false otherwise
     */
    public boolean evaluate(Double actual, Double limit) {
        if (actual == null || limit == null) {
            return false;
        }
        return evaluator.apply(actual, limit);
    }

    /**
     * Generates human-readable criterion string
     *
     * @param fieldName The field being compared
     * @param limit     The limit value
     * @param unit      The unit of measurement
     * @return e.g., "MKT ≤ 20.0 °C"
     */
    public String generateDisplayString(String fieldName, Double limit, String unit) {
        return fieldName + " " + symbol + " " + String.format("%.2f", limit) + " " + unit;
    }
}
