package com.mac.bry.validationsystem.wizard.criteria;

import com.mac.bry.validationsystem.wizard.ValidationDraft;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Custom acceptance criterion defined in wizard step 3.
 *
 * <p>
 * Each criterion specifies a field to evaluate, an operator, and a limit value.
 * Example: "MKT ≤ 20.0°C", "STD_DEV < 5.0°C", "COMPLIANCE_PCT ≥ 95%"
 * </p>
 *
 * Many-to-one: Each ValidationDraft can have multiple custom criteria.
 */
@Entity
@Table(name = "custom_acceptance_criteria")
@Audited
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomAcceptanceCriterion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The validation draft this criterion belongs to
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validation_draft_id", nullable = false)
    private ValidationDraft validationDraft;

    /**
     * Which field to evaluate (MIN_TEMP, MAX_TEMP, AVG_TEMP, MKT, STD_DEV, etc.)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "field_name", nullable = false, length = 100)
    private AcceptanceCriterionField fieldName;

    /**
     * Comparison operator (GT, LT, GTE, LTE, EQ)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "operator", nullable = false, length = 10)
    private CriterionOperator operator;

    /**
     * The limit value to compare against (e.g., 20.0 for MKT ≤ 20.0)
     */
    @Column(name = "limit_value", nullable = false, precision = 10, scale = 4)
    private BigDecimal limitValue;

    /**
     * Unit of measurement (°C, %, min, count, etc.)
     * Auto-populated from fieldName.getUnit()
     */
    @Column(name = "unit", nullable = true, length = 20)
    private String unit;

    /**
     * Display order in step 3 UI (for sorting custom criteria rows)
     */
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    /**
     * Flag: is this a standard GMP criterion (vs. user-custom)
     * Default: false for user-defined criteria, true for standard ones
     */
    @Column(name = "is_standard", nullable = false)
    private Boolean isStandard;

    /**
     * Timestamp of creation
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ========== UTILITY METHODS ==========

    /**
     * Generates human-readable display string for UI
     * e.g., "MKT ≤ 20.0 °C"
     */
    public String getDisplayString() {
        return operator.generateDisplayString(
            fieldName.getDisplayName(),
            limitValue != null ? limitValue.doubleValue() : 0.0,
            unit
        );
    }

    /**
     * Evaluates whether an actual value passes this criterion
     * @param actualValue The measured value
     * @return true if criterion is met
     */
    public boolean evaluate(Double actualValue) {
        return operator.evaluate(actualValue, limitValue != null ? limitValue.doubleValue() : 0.0);
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        // Auto-populate unit from field if not set
        if (unit == null && fieldName != null) {
            unit = fieldName.getUnit();
        }
        if (isStandard == null) {
            isStandard = false;
        }
    }
}
