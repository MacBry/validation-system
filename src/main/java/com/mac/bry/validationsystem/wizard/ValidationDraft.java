package com.mac.bry.validationsystem.wizard;

import com.mac.bry.validationsystem.device.CoolingDevice;
import com.mac.bry.validationsystem.measurement.RecorderPosition;
import com.mac.bry.validationsystem.validation.DeviceLoadState;
import com.mac.bry.validationsystem.validation.Validation;
import com.mac.bry.validationsystem.wizard.criteria.CustomAcceptanceCriterion;
import com.mac.bry.validationsystem.wizard.converter.LongListJsonConverter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistent state of a multi-step validation wizard session.
 *
 * <p>
 * One row per wizard = one validation being created step-by-step (9 steps).
 * Tracks procedure type (OQ/PQ/MAPPING), current step, step locks, and finalization state.
 * </p>
 *
 * Lifecycle:
 * - Step 1: procedure_type selected
 * - Step 2: cooling_device selected
 * - Step 3: custom_acceptance_criteria added; stepLockFrom = 3 (lock steps 2-3)
 * - Step 4: device_load_state and recorder_position selected
 * - Step 5: OQ or PQ specific data (OqTestResult / PqChecklistItems)
 * - Step 6: selected_series_ids populated (JSON array)
 * - Step 7-8: Review and statistics
 * - Step 9: Finalize, sign, create Validation entity → completed_validation_id set
 */
@Entity
@Table(name = "validation_drafts")
@Audited
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidationDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ========== WHO & WHEN ==========

    /**
     * Username of wizard creator (for authorization)
     */
    @Column(name = "created_by", nullable = false)
    private String createdBy;

    /**
     * Timestamp of draft creation
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp of last update
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ========== PROCEDURE TYPE & STEP TRACKING ==========

    /**
     * Type of qualification procedure: OQ, PQ, or MAPPING (selected in step 1)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "procedure_type", nullable = false)
    private ValidationProcedureType procedureType;

    /**
     * Current step in wizard (1-9)
     */
    @Column(name = "current_step", nullable = false)
    private Integer currentStep;

    /**
     * Step lock: once step 3 criteria are saved, steps 2-3 cannot be navigated back to.
     * NULL = no lock; 3 = lock from step 3 (user locked into device and criteria choices)
     */
    @Column(name = "step_lock_from", nullable = true)
    private Integer stepLockFrom;

    /**
     * Overall status of the wizard: IN_PROGRESS, COMPLETED, ABANDONED
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private WizardStatus status;

    // ========== STEP 2: DEVICE SELECTION ==========

    /**
     * The cooling device selected in step 2 (nullable until step 2)
     */
    @JsonIgnore
    @NotAudited
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cooling_device_id", nullable = true)
    private CoolingDevice coolingDevice;

    // ========== STEP 3: CUSTOM ACCEPTANCE CRITERIA ==========

    /**
     * Custom acceptance criteria defined in step 3 (one-to-many)
     * Cascade ALL, orphanRemoval for clean deletion
     */
    @JsonIgnore
    @OneToMany(
        mappedBy = "validationDraft",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    private List<CustomAcceptanceCriterion> customAcceptanceCriteria = new ArrayList<>();

    // ========== STEP 4: LOAD STATE & SENSOR POSITION ==========

    /**
     * Device load state during validation: EMPTY, FULL, PARTIALLY_LOADED
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "device_load_state", nullable = true)
    private DeviceLoadState deviceLoadState;

    /**
     * Recorder position (control sensor) within device volume
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "recorder_position", nullable = true)
    private RecorderPosition recorderPosition;

    // ========== STEP 5: OQ/PQ SPECIFIC DATA ==========

    /**
     * OQ test results (1-to-1 if procedure_type == OQ)
     * Loaded on-demand via service
     */
    // Lazy-loaded via OqTestResultRepository.findByValidationDraftId()

    /**
     * PQ checklist items (N-to-1 if procedure_type == PQ)
     * Loaded on-demand via service
     */
    // Lazy-loaded via PqChecklistItemRepository.findByValidationDraftId()

    // ========== STEP 6: MEASUREMENT SERIES SELECTION ==========

    /**
     * JSON array of selected measurement_series IDs: [1, 2, 5, 10, ...]
     * Converted to/from JSON using LongListJsonConverter
     */
    @Convert(converter = LongListJsonConverter.class)
    @Column(name = "selected_series_ids", columnDefinition = "LONGTEXT", nullable = true)
    @Builder.Default
    private List<Long> selectedSeriesIds = new ArrayList<>();

    // ========== PLAN DATA (PERIODIC_REVALIDATION only) ==========

    /**
     * Validation plan data for PERIODIC_REVALIDATION procedure type.
     * NULL for OQ, PQ, and MAPPING procedures.
     * Contains plan details, mapping status, acceptance criteria, signatures, and QA approval.
     */
    @JsonIgnore
    @NotAudited
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "plan_data_id", unique = true, nullable = true)
    private ValidationPlanData planData;

    // ========== STEP 9: FINALIZATION ==========

    /**
     * The Validation entity created after finalization (step 9)
     * Set after signing and Validation creation; marks draft as COMPLETED
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_validation_id", nullable = true)
    private Validation completedValidation;

    // ========== UTILITY METHODS ==========

    /**
     * Check if wizard can navigate back from current step to target step
     * @param targetStep The step to navigate to
     * @return true if navigation is allowed
     */
    public boolean canNavigateBack(Integer targetStep) {
        if (stepLockFrom == null) {
            return true; // No lock
        }
        return targetStep >= stepLockFrom; // Can only go forward or to lock point
    }

    /**
     * Checks if this draft is in an active (fillable) state
     */
    public boolean isActive() {
        return status == WizardStatus.IN_PROGRESS;
    }

    /**
     * Checks if OQ tests are required for this procedure
     */
    public boolean requiresOqTests() {
        return procedureType == ValidationProcedureType.OQ;
    }

    /**
     * Checks if PQ checklist is required for this procedure
     */
    public boolean requiresPqChecklist() {
        return procedureType == ValidationProcedureType.PQ;
    }

    /**
     * Checks if this draft requires a validation plan (PERIODIC_REVALIDATION)
     */
    public boolean requiresValidationPlan() {
        return procedureType != null && procedureType.requiresValidationPlan();
    }

    /**
     * Checks if the plan has been fully approved (technician signed + QA approved)
     * Returns false for non-PERIODIC_REVALIDATION procedures.
     */
    public boolean isPlanFullyApproved() {
        return planData != null && planData.isFullyApproved();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = WizardStatus.IN_PROGRESS;
        }
        if (currentStep == null) {
            currentStep = 1;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
