package com.mac.bry.validationsystem.wizard.pdf;

import com.itextpdf.layout.Document;
import com.itextpdf.kernel.font.PdfFont;
import com.mac.bry.validationsystem.validation.Validation;
import com.mac.bry.validationsystem.wizard.ValidationDraft;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Mapping (Temperature Mapping) procedure strategy.
 *
 * <p>
 * No-op strategy for MAPPING procedures - uses standard validation protocol
 * without additional OQ/PQ specific sections.
 * </p>
 */
@Slf4j
@Component
public class MappingProcedureStrategy implements ValidationProcedureStrategy {

    @Override
    public void addProcedureSpecificSections(
        Document document,
        PdfFont pdfFont,
        Validation validation,
        ValidationDraft draft) {

        log.debug("MAPPING procedure uses standard validation protocol, no additional sections");
        // No-op: standard protocol used
    }

    @Override
    public String getProcedureName() {
        return "Mapowanie - Pole Temperaturowe";
    }

    @Override
    public String getProcedureDescription() {
        return "Temperature mapping without OQ/PQ procedures (standard protocol)";
    }
}
