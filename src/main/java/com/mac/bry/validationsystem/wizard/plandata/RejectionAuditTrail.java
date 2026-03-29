package com.mac.bry.validationsystem.wizard.plandata;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Value object for QA rejection audit trail.
 *
 * <p>
 * When QA rejects the validation plan, the reason, timestamp, and rejecting user
 * are recorded. The rejection count is tracked for audit purposes.
 * The technician sees the rejection reason as "Komentarz QA do poprawy".
 * </p>
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RejectionAuditTrail {

    /**
     * Reason provided by QA for rejecting the plan
     */
    @Column(name = "plan_rejection_reason", columnDefinition = "LONGTEXT")
    private String rejectionReason;

    /**
     * Timestamp of the rejection
     */
    @Column(name = "plan_rejected_at")
    private LocalDateTime rejectedAt;

    /**
     * Username of the QA user who rejected the plan
     */
    @Column(name = "plan_rejected_by", length = 50)
    private String rejectedByUsername;

    /**
     * Number of rejection attempts (for audit trail)
     */
    @Column(name = "rejection_attempt_count")
    private Integer rejectionAttemptCount;

    /**
     * Checks if the plan has been rejected
     */
    public boolean isRejected() {
        return rejectedAt != null;
    }
}
