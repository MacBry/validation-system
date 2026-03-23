package com.mac.bry.validationsystem.wizard.pdf;

import com.itextpdf.layout.Document;
import com.itextpdf.kernel.font.PdfFont;
import com.mac.bry.validationsystem.validation.Validation;
import com.mac.bry.validationsystem.wizard.ValidationDraft;
import com.mac.bry.validationsystem.wizard.pq.PqChecklistItem;
import com.mac.bry.validationsystem.wizard.pq.PqChecklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * PQ (Performance Qualification) procedure strategy.
 *
 * <p>
 * Adds PQ-specific checklist section to the validation PDF:
 * - 10-item checklist (PQ-01..PQ-10) with pass/fail status and comments
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PqProcedureStrategy implements ValidationProcedureStrategy {

    private final PqChecklistService pqChecklistService;

    @Override
    public void addProcedureSpecificSections(
        Document document,
        PdfFont pdfFont,
        Validation validation,
        ValidationDraft draft) {

        log.debug("Adding PQ-specific sections to PDF for validation: {}", validation.getId());

        if (draft == null) {
            log.warn("ValidationDraft is null for PQ procedure, skipping PQ-specific sections");
            return;
        }

        // Load PQ checklist items
        List<PqChecklistItem> items = pqChecklistService.findByDraftId(draft.getId());

        if (items.isEmpty()) {
            log.warn("No PQ checklist items found for draft: {}", draft.getId());
            return;
        }

        // Add PQ checklist table to PDF
        // TODO: Implement table rendering with iText 7
        // Table should include:
        // 1. Item code (PQ-01, PQ-02, ...)
        // 2. Item description
        // 3. Pass/Fail status
        // 4. Comments

        log.debug("PQ checklist: {} items, {} passed",
            items.size(),
            pqChecklistService.getPassedItemCount(draft.getId()));
    }

    @Override
    public String getProcedureName() {
        return "PQ - Kwalifikacja Wydajności";
    }

    @Override
    public String getProcedureDescription() {
        return "Performance Qualification with 10-item checklist";
    }
}
