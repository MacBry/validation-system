package com.mac.bry.validationsystem.wizard.oq;

import com.mac.bry.validationsystem.wizard.ValidationDraft;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;

import java.time.LocalDateTime;

/**
 * OQ test results for Operational Qualification wizard (step 5, OQ procedure only).
 *
 * <p>
 * Stores results of three OQ tests:
 * 1. Power Failure Test (Awaria zasilania)
 * 2. Alarm Verification (Weryfikacja alarmu)
 * 3. Door Lock Test (Test drzwi)
 * </p>
 *
 * One-to-one semantics: Each ValidationDraft (if procedure_type == OQ) has exactly one OqTestResult.
 */
@Entity
@Table(name = "oq_test_results")
@Audited
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OqTestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The validation draft (1-to-1, unique constraint)
     */
    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validation_draft_id", nullable = false, unique = true)
    private ValidationDraft validationDraft;

    // ========== POWER FAILURE TEST ==========

    /**
     * Result of power failure test: true=PASSED, false=FAILED, null=not tested
     */
    @Column(name = "power_failure_test_passed", nullable = true)
    private Boolean powerFailureTestPassed;

    /**
     * Notes or comments about power failure test
     */
    @Column(name = "power_failure_notes", nullable = true, length = 1000)
    private String powerFailureNotes;

    // ========== ALARM VERIFICATION TEST ==========

    /**
     * Result of alarm verification test: true=PASSED, false=FAILED, null=not tested
     */
    @Column(name = "alarm_test_passed", nullable = true)
    private Boolean alarmTestPassed;

    /**
     * Notes or comments about alarm test
     */
    @Column(name = "alarm_test_notes", nullable = true, length = 1000)
    private String alarmTestNotes;

    // ========== DOOR LOCK TEST ==========

    /**
     * Result of door lock test: true=PASSED, false=FAILED, null=not tested
     */
    @Column(name = "door_test_passed", nullable = true)
    private Boolean doorTestPassed;

    /**
     * Notes or comments about door test
     */
    @Column(name = "door_test_notes", nullable = true, length = 1000)
    private String doorTestNotes;

    // ========== METADATA ==========

    /**
     * Username of user who filled in the test results
     */
    @Column(name = "created_by", nullable = false)
    private String createdBy;

    /**
     * Timestamp of creation
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp of last update
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ========== UTILITY METHODS ==========

    /**
     * Checks if all three tests have been completed
     */
    public boolean allTestsCompleted() {
        return powerFailureTestPassed != null
            && alarmTestPassed != null
            && doorTestPassed != null;
    }

    /**
     * Checks if all three tests passed
     */
    public boolean allTestsPassed() {
        return allTestsCompleted()
            && powerFailureTestPassed
            && alarmTestPassed
            && doorTestPassed;
    }

    /**
     * Counts how many tests have been completed
     */
    public int completedTestCount() {
        int count = 0;
        if (powerFailureTestPassed != null) count++;
        if (alarmTestPassed != null) count++;
        if (doorTestPassed != null) count++;
        return count;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
