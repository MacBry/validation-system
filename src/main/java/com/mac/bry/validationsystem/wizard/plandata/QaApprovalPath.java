package com.mac.bry.validationsystem.wizard.plandata;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Value object for QA approval of the validation plan.
 *
 * <p>
 * Supports two approval paths per FDA 21 CFR Part 11:
 * <ul>
 *   <li>Path A: Electronic signature - QA signs within the system</li>
 *   <li>Path B: Scanned document - QA signs a printed plan, scan is uploaded</li>
 * </ul>
 * </p>
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QaApprovalPath {

    /**
     * Which approval method was used
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "plan_qa_approval_method", length = 20)
    private QaApprovalMethod approvalMethod;

    // ========== Path A: Electronic signature ==========

    /**
     * Timestamp of QA electronic signature
     */
    @Column(name = "plan_qa_signed_at")
    private LocalDateTime electronicSignedAt;

    /**
     * Username of the QA user who signed electronically
     */
    @Column(name = "plan_qa_username", length = 50)
    private String electronicUsername;

    /**
     * Full name of the QA signer
     */
    @Column(name = "plan_qa_full_name", length = 200)
    private String electronicFullName;

    // ========== Path B: Scanned document ==========

    /**
     * File path to the scanned signed PDF
     */
    @Column(name = "plan_qa_scanned_document_path", length = 500)
    private String scannedDocumentPath;

    /**
     * Timestamp when the scanned document was uploaded
     */
    @Column(name = "plan_qa_scanned_uploaded_at")
    private LocalDateTime scannedUploadedAt;

    /**
     * Username of the internal user who uploaded the scanned document
     */
    @Column(name = "plan_qa_scanned_uploaded_by", length = 50)
    private String scannedUploadedBy;

    // ========== Domain logic ==========

    /**
     * Checks if the plan has been approved through either path
     */
    public boolean isApproved() {
        if (approvalMethod == null) {
            return false;
        }
        switch (approvalMethod) {
            case ELECTRONIC_SIGNATURE:
                return electronicSignedAt != null;
            case SCANNED_DOCUMENT:
                return scannedUploadedAt != null && scannedDocumentPath != null;
            default:
                return false;
        }
    }
}
