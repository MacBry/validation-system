package com.mac.bry.validationsystem.wizard.pdf;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.layout.Document;
import com.itextpdf.kernel.font.PdfFont;
import com.mac.bry.validationsystem.validation.Validation;
import com.mac.bry.validationsystem.wizard.ValidationDraft;

/**
 * Strategy interface for procedure-specific PDF sections in validation protocols.
 *
 * <p>
 * Allows different procedure types (OQ/PQ/MAPPING) to add their own custom sections
 * to the validation PDF report.
 * </p>
 *
 * Used in:
 * - OqProcedureStrategy: Adds OQ test results tables
 * - PqProcedureStrategy: Adds PQ checklist table
 * - MappingProcedureStrategy: No-op (standard protocol)
 */
public interface ValidationProcedureStrategy {

    /**
     * Add procedure-specific sections to the PDF document
     *
     * @param document The iText Document being built
     * @param pdfFont Font to use for text rendering
     * @param validation The finalized Validation entity
     * @param draft The associated ValidationDraft (may be null for non-wizard validations)
     */
    void addProcedureSpecificSections(
        Document document,
        PdfFont pdfFont,
        Validation validation,
        ValidationDraft draft
    );

    /**
     * Get the display name of this procedure for documentation purposes
     */
    String getProcedureName();

    /**
     * Get a short description of this procedure type
     */
    String getProcedureDescription();
}
