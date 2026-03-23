package com.mac.bry.validationsystem.wizard.pdf;

import com.itextpdf.layout.Document;
import com.itextpdf.kernel.font.PdfFont;
import com.mac.bry.validationsystem.validation.Validation;
import com.mac.bry.validationsystem.wizard.ValidationDraft;
import com.mac.bry.validationsystem.wizard.oq.OqTestResult;
import com.mac.bry.validationsystem.wizard.oq.OqTestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * OQ (Operational Qualification) procedure strategy.
 *
 * <p>
 * Adds OQ-specific test result sections to the validation PDF:
 * - Power failure test (Awaria zasilania)
 * - Alarm verification (Weryfikacja alarmu)
 * - Door lock test (Test drzwi)
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OqProcedureStrategy implements ValidationProcedureStrategy {

    private final OqTestService oqTestService;

    @Override
    public void addProcedureSpecificSections(
        Document document,
        PdfFont pdfFont,
        Validation validation,
        ValidationDraft draft) {

        log.debug("Adding OQ-specific sections to PDF for validation: {}", validation.getId());

        if (draft == null) {
            log.warn("ValidationDraft is null for OQ procedure, skipping OQ-specific sections");
            return;
        }

        // Load OQ test results
        Optional<OqTestResult> oqResult = oqTestService.findByDraftId(draft.getId());

        if (!oqResult.isPresent()) {
            log.warn("No OQ test results found for draft: {}", draft.getId());
            return;
        }

        OqTestResult result = oqResult.get();

        // Add OQ test results section to PDF
        // TODO: Implement table rendering with iText 7
        // Section should include:
        // 1. Power Failure Test (Awaria zasilania): result + notes
        // 2. Alarm Verification (Weryfikacja alarmu): result + notes
        // 3. Door Lock Test (Test drzwi): result + notes

        log.debug("OQ test results: power={}, alarm={}, door={}",
            result.getPowerFailureTestPassed(),
            result.getAlarmTestPassed(),
            result.getDoorTestPassed());
    }

    @Override
    public String getProcedureName() {
        return "OQ - Kwalifikacja Operacyjna";
    }

    @Override
    public String getProcedureDescription() {
        return "Operational Qualification with power, alarm, and door lock tests";
    }
}
