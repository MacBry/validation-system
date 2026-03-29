package com.mac.bry.validationsystem.wizard.plandata;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Value object for generated plan PDF metadata.
 *
 * <p>
 * Stores the path, document number, and generation timestamp of the
 * signed validation plan PDF. The PDF is generated after the technician
 * signs the plan at step 8.
 * </p>
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlanPdfInfo {

    /**
     * File path to the generated plan PDF
     */
    @Column(name = "plan_pdf_path", length = 500)
    private String pdfPath;

    /**
     * Assigned document number (e.g., "VP-2024-001")
     */
    @Column(name = "plan_document_number", length = 100)
    private String documentNumber;

    /**
     * Timestamp when the PDF was generated
     */
    @Column(name = "plan_pdf_generated_at")
    private LocalDateTime generatedAt;

    /**
     * Checks if a PDF has been generated for this plan
     */
    public boolean isGenerated() {
        return pdfPath != null && generatedAt != null;
    }
}
