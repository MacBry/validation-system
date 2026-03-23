package com.mac.bry.validationsystem.wizard.service;

import com.mac.bry.validationsystem.stats.ValidationSummaryStatsDto;
import com.mac.bry.validationsystem.wizard.criteria.CustomAcceptanceCriterion;
import com.mac.bry.validationsystem.wizard.criteria.CriterionEvaluationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for evaluating acceptance criteria against validation summary statistics.
 *
 * <p>
 * Provides methods to:
 * - Extract metric values from ValidationSummaryStatsDto (9 different metrics)
 * - Evaluate custom criteria against actual values
 * - Generate pass/fail reports
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AcceptanceCriteriaEvaluatorService {

    /**
     * Evaluate a single criterion against stats
     * @param criterion The criterion to evaluate
     * @param stats The validation summary statistics
     * @return Evaluation result with pass/fail and actual/limit values
     */
    public CriterionEvaluationResult evaluateCriterion(CustomAcceptanceCriterion criterion, ValidationSummaryStatsDto stats) {
        log.debug("Evaluating criterion: {} against stats", criterion.getDisplayString());

        Double actualValue = extractFieldValue(criterion.getFieldName().name(), stats);
        boolean passed = criterion.evaluate(actualValue);

        return CriterionEvaluationResult.builder()
            .fieldName(criterion.getFieldName().getDisplayName())
            .operator(criterion.getOperator())
            .limitValue(criterion.getLimitValue() != null ? criterion.getLimitValue().doubleValue() : null)
            .actualValue(actualValue)
            .unit(criterion.getUnit())
            .passed(passed)
            .message(generateMessage(criterion, actualValue, passed))
            .build();
    }

    /**
     * Evaluate all criteria against stats
     * @param criteria List of criteria to evaluate
     * @param stats The validation summary statistics
     * @return List of evaluation results
     */
    public List<CriterionEvaluationResult> evaluateAll(List<CustomAcceptanceCriterion> criteria, ValidationSummaryStatsDto stats) {
        List<CriterionEvaluationResult> results = new ArrayList<>();

        for (CustomAcceptanceCriterion criterion : criteria) {
            results.add(evaluateCriterion(criterion, stats));
        }

        return results;
    }

    /**
     * Check if all criteria are passed
     * @param results List of evaluation results
     * @return true if all passed, false otherwise
     */
    public boolean allPassed(List<CriterionEvaluationResult> results) {
        return results.stream().allMatch(r -> r.getPassed() != null && r.getPassed());
    }

    /**
     * Extract a metric value from statistics by field name
     * Maps 9 different metrics to their corresponding extraction functions
     */
    private Double extractFieldValue(String fieldName, ValidationSummaryStatsDto stats) {
        if (stats == null) {
            return null;
        }

        switch (fieldName) {
            case "MIN_TEMP":
                return stats.getGlobalMinTemp();
            case "MAX_TEMP":
                return stats.getGlobalMaxTemp();
            case "AVG_TEMP":
                return stats.getOverallAvgTemp();
            case "MKT":
                return stats.getGlobalMkt();
            case "STD_DEV":
                return stats.getGlobalStdDev();
            case "DRIFT_COEFFICIENT":
                return stats.getAvgTrendCoefficient();
            case "COMPLIANCE_PCT":
                return stats.getGlobalCompliancePercentage();
            case "MAX_VIOLATION_DURATION":
                return stats.getMaxViolationDurationMinutes() != null
                    ? stats.getMaxViolationDurationMinutes().doubleValue()
                    : null;
            case "TOTAL_VIOLATIONS":
                return stats.getTotalViolations() != null
                    ? stats.getTotalViolations().doubleValue()
                    : null;
            default:
                log.warn("Unknown field name: {}", fieldName);
                return null;
        }
    }

    /**
     * Generate a human-readable message for criterion evaluation result
     */
    private String generateMessage(CustomAcceptanceCriterion criterion, Double actualValue, boolean passed) {
        if (actualValue == null) {
            return "Nie można obliczyć wartości";
        }

        String result = passed ? "✅ SPEŁNIONE" : "❌ NIESPEŁNIONE";
        String comparisonStr = criterion.getOperator().generateDisplayString(
            criterion.getFieldName().getDisplayName(),
            criterion.getLimitValue() != null ? criterion.getLimitValue().doubleValue() : 0.0,
            criterion.getUnit()
        );

        return String.format("%s: %s (wartość: %.2f %s)",
            result,
            comparisonStr,
            actualValue,
            criterion.getUnit());
    }
}
