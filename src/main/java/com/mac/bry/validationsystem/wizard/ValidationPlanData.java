package com.mac.bry.validationsystem.wizard;

import com.mac.bry.validationsystem.wizard.plandata.DeviationProcedures;
import com.mac.bry.validationsystem.wizard.plandata.MappingInfo;
import com.mac.bry.validationsystem.wizard.plandata.PlanAcceptanceCriteria;
import com.mac.bry.validationsystem.wizard.plandata.PlanDetails;
import com.mac.bry.validationsystem.wizard.plandata.PlanPdfInfo;
import com.mac.bry.validationsystem.wizard.plandata.QaApprovalPath;
import com.mac.bry.validationsystem.wizard.plandata.RejectionAuditTrail;
import com.mac.bry.validationsystem.wizard.plandata.TechnikSignature;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;

import java.time.LocalDateTime;

/**
 * Persistent state of the validation plan for PERIODIC_REVALIDATION procedure.
 *
 * <p>
 * One row per periodic revalidation draft = one validation plan.
 * Linked to {@link ValidationDraft} via a OneToOne relationship (plan_data_id FK on draft).
 * </p>
 *
 * <p>
 * The plan is created during Phase 1 (steps 3-8) of the two-phase wizard:
 * <ul>
 *   <li>Step 3: Plan details (reason, previous validation reference)</li>
 *   <li>Step 4: Mapping status check (auto-filled from DB)</li>
 *   <li>Steps 5-6: Acceptance criteria and load state</li>
 *   <li>Step 7: Deviation/CAPA procedures</li>
 *   <li>Step 8: Technician signature + PDF generation</li>
 *   <li>QA approval: External action by QA user</li>
 * </ul>
 * </p>
 *
 * <p>
 * Compliance: GMP Annex 15 section 10 - documented validation plan before measurement phase.
 * FDA 21 CFR Part 11 - electronic signatures with 2-person co-signature.
 * </p>
 *
 * @see ValidationDraft
 * @see MappingStatus
 */
@Entity
@Table(name = "validation_plan_data")
@Audited
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidationPlanData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ========== STEP 3: PLAN DETAILS ==========

    @Embedded
    @Builder.Default
    private PlanDetails details = new PlanDetails();

    // ========== STEP 4: MAPPING STATUS ==========

    @Embedded
    @Builder.Default
    private MappingInfo mappingInfo = new MappingInfo();

    // ========== STEPS 5-6: ACCEPTANCE CRITERIA ==========

    @Embedded
    @Builder.Default
    private PlanAcceptanceCriteria acceptanceCriteria = new PlanAcceptanceCriteria();

    // ========== STEP 7: DEVIATION PROCEDURES ==========

    @Embedded
    @Builder.Default
    private DeviationProcedures deviationProcedures = new DeviationProcedures();

    // ========== STEP 8: TECHNIK SIGNATURE ==========

    @Embedded
    @Builder.Default
    private TechnikSignature technikSignature = new TechnikSignature();

    // ========== QA APPROVAL ==========

    @Embedded
    @Builder.Default
    private QaApprovalPath qaApproval = new QaApprovalPath();

    // ========== QA REJECTION AUDIT ==========

    @Embedded
    @Builder.Default
    private RejectionAuditTrail rejectionTrail = new RejectionAuditTrail();

    // ========== GENERATED PDF ==========

    @Embedded
    @Builder.Default
    private PlanPdfInfo pdfInfo = new PlanPdfInfo();

    // ========== TIMESTAMPS ==========

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ========== DOMAIN LOGIC ==========

    /**
     * Checks if the technician has signed the plan
     */
    public boolean isTechnikSigned() {
        return technikSignature != null && technikSignature.isSigned();
    }

    /**
     * Checks if QA has approved the plan (through either electronic or scanned path)
     */
    public boolean isQaApproved() {
        return qaApproval != null && qaApproval.isApproved();
    }

    /**
     * Checks if the plan has been rejected by QA
     */
    public boolean isRejected() {
        return rejectionTrail != null && rejectionTrail.isRejected();
    }

    /**
     * Checks if the mapping status requires acknowledgement and has not been acknowledged
     */
    public boolean needsMappingAcknowledgement() {
        return mappingInfo != null && mappingInfo.needsAcknowledgement();
    }

    /**
     * Checks if a plan PDF has been generated
     */
    public boolean isPdfGenerated() {
        return pdfInfo != null && pdfInfo.isGenerated();
    }

    /**
     * Checks if the plan is fully complete (signed by technician and approved by QA)
     */
    public boolean isFullyApproved() {
        return isTechnikSigned() && isQaApproved();
    }

    // ========== LIFECYCLE ==========

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
