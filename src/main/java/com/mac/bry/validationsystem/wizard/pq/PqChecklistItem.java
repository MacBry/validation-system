package com.mac.bry.validationsystem.wizard.pq;

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
 * PQ checklist item for Performance Qualification wizard (step 5, PQ procedure only).
 *
 * <p>
 * Each item in the PQ checklist (PQ-01, PQ-02, ..., PQ-10+) with pass/fail result and comment.
 * </p>
 *
 * Many-to-one: Each ValidationDraft (if procedure_type == PQ) has 10+ PQ checklist rows.
 */
@Entity
@Table(name = "pq_checklist_items")
@Audited
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PqChecklistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The validation draft this item belongs to
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validation_draft_id", nullable = false)
    private ValidationDraft validationDraft;

    /**
     * Item code in format "PQ-XX" (PQ-01, PQ-02, ..., PQ-10, etc.)
     */
    @Column(name = "item_code", nullable = false, length = 10)
    private String itemCode;

    /**
     * Human-readable description of this checklist item
     */
    @Column(name = "item_description", nullable = false, length = 500)
    private String itemDescription;

    /**
     * Pass/fail result: true=PASSED, false=FAILED, null=NOT_ASSESSED
     */
    @Column(name = "passed", nullable = true)
    private Boolean passed;

    /**
     * Comment or notes about this checklist item
     */
    @Column(name = "comment", nullable = true, length = 1000)
    private String comment;

    /**
     * Display order in step 5 UI (for sorting PQ items)
     */
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    /**
     * Username of user who filled in this item
     */
    @Column(name = "created_by", nullable = true)
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
     * Checks if this item has been assessed (passed or failed)
     */
    public boolean isAssessed() {
        return passed != null;
    }

    /**
     * Checks if this item passed
     */
    public boolean hasPassed() {
        return passed != null && passed;
    }

    /**
     * Checks if this item failed
     */
    public boolean hasFailed() {
        return passed != null && !passed;
    }

    /**
     * Returns human-readable status badge
     */
    public String getStatusBadge() {
        if (!isAssessed()) {
            return "Nie ocenione";
        }
        return hasPassed() ? "✅ Spełnione" : "❌ Niespełnione";
    }

    /**
     * Returns CSS class for status badge styling
     */
    public String getStatusCssClass() {
        if (!isAssessed()) {
            return "badge-secondary";
        }
        return hasPassed() ? "badge-success" : "badge-danger";
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
