package com.mac.bry.validationsystem.wizard.pdf;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.mac.bry.validationsystem.device.CoolingDevice;
import com.mac.bry.validationsystem.measurement.RecorderPosition;
import com.mac.bry.validationsystem.validation.PdfSigningService;
import com.mac.bry.validationsystem.wizard.ValidationDraft;
import com.mac.bry.validationsystem.wizard.ValidationDraftRepository;
import com.mac.bry.validationsystem.wizard.ValidationPlanData;
import com.mac.bry.validationsystem.wizard.plandata.DeviationProcedures;
import com.mac.bry.validationsystem.wizard.plandata.MappingInfo;
import com.mac.bry.validationsystem.wizard.plandata.PlanAcceptanceCriteria;
import com.mac.bry.validationsystem.wizard.plandata.PlanDetails;
import com.mac.bry.validationsystem.wizard.plandata.QaApprovalPath;
import com.mac.bry.validationsystem.wizard.plandata.TechnikSignature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * Generates the Validation Plan PDF for PERIODIC_REVALIDATION wizard drafts.
 *
 * <p>
 * The plan PDF is required by GMP Annex 15 §10 before measurements begin.
 * It is generated at step 8 (technician signature), signed using
 * {@link PdfSigningService} with a TSA timestamp, and cached in Redis for
 * 24 hours to avoid regeneration on every QA review page load.
 * </p>
 *
 * <p>
 * PDF structure:
 * <ul>
 *   <li>Section A — Plan details (reason, previous validation reference)</li>
 *   <li>Section B — Mapping status (current / overdue / never)</li>
 *   <li>Section C — Acceptance criteria and load state</li>
 *   <li>Section D — Sensor position summary</li>
 *   <li>Section E — CAPA procedures (critical / major / minor deviations)</li>
 *   <li>Section F — Signatures (technician + QA, if approved)</li>
 * </ul>
 * </p>
 *
 * @see PdfSigningService
 * @see com.mac.bry.validationsystem.wizard.service.WizardFinalizationService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValidationPlanPdfService {

    // ── Colour palette (aligned with ValidationProtocolPdfService) ───────────
    private static final DeviceRgb CLR_TITLE    = new DeviceRgb(31,  73,  125);
    private static final DeviceRgb CLR_SECTION  = new DeviceRgb(68,  114, 196);
    private static final DeviceRgb CLR_HDR      = new DeviceRgb(189, 215, 238);
    private static final DeviceRgb CLR_META_LBL = new DeviceRgb(242, 242, 242);
    private static final DeviceRgb CLR_OK       = new DeviceRgb(16,  185, 129);
    private static final DeviceRgb CLR_WARN     = new DeviceRgb(245, 158,  11);
    private static final DeviceRgb CLR_DANGER   = new DeviceRgb(220,  38,  38);

    private static final float BORDER = 0.5f;
    private static final DateTimeFormatter DT_FMT   = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter DATE_FMT  = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /** Redis key prefix for cached plan PDFs. */
    private static final String CACHE_KEY_PREFIX = "validation-plan-pdf:";
    /** Cache TTL — 24 hours. */
    private static final long CACHE_TTL_HOURS = 24;

    private final ValidationDraftRepository draftRepository;
    private final PdfSigningService pdfSigningService;
    private final RedisTemplate<String, byte[]> redisTemplate;

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Generates the complete plan PDF for the given draft and signs it with the
     * organisation certificate (TSA timestamp included).
     *
     * @param draftId ID of a PERIODIC_REVALIDATION draft with plan data populated
     * @return signed PDF bytes
     * @throws IllegalArgumentException if the draft or its plan data is not found
     * @throws IllegalStateException    if the draft has no cooling device
     * @throws IOException              if iText PDF generation fails
     * @throws Exception                if TSA signing fails
     */
    public byte[] generatePlanPdf(Long draftId) throws Exception {
        log.info("Generating validation plan PDF for draft: {}", draftId);

        ValidationDraft draft = draftRepository.findById(draftId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Validation draft not found: " + draftId));

        if (draft.getPlanData() == null) {
            throw new IllegalStateException(
                "Draft " + draftId + " has no plan data — cannot generate plan PDF");
        }

        if (draft.getCoolingDevice() == null) {
            throw new IllegalStateException(
                "Draft " + draftId + " has no cooling device — cannot generate plan PDF");
        }

        byte[] unsignedBytes = buildPdfBytes(draft);
        log.info("Generated unsigned plan PDF: {} bytes for draft: {}", unsignedBytes.length, draftId);

        byte[] signedBytes = pdfSigningService.signPdf(
            unsignedBytes,
            "Plan Walidacji Okresowej - podpis technologa",
            draft.getCoolingDevice().getLaboratory() != null
                ? draft.getCoolingDevice().getLaboratory().getFullName()
                : "Laboratorium");

        log.info("Signed plan PDF: {} bytes for draft: {}", signedBytes.length, draftId);
        return signedBytes;
    }

    /**
     * Returns the plan PDF for the given draft, using a 24-hour Redis cache.
     *
     * <p>
     * On cache miss, delegates to {@link #generatePlanPdf(Long)} and stores
     * the result in Redis before returning. Call {@link #invalidateCache(Long)}
     * whenever plan data changes (e.g., after QA rejection and re-submission).
     * </p>
     *
     * @param draftId ID of a PERIODIC_REVALIDATION draft
     * @return signed PDF bytes (possibly from cache)
     */
    public byte[] getPlanPdfWithCache(Long draftId) throws Exception {
        String key = CACHE_KEY_PREFIX + draftId;

        byte[] cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            log.debug("Plan PDF cache hit for draft: {}", draftId);
            return cached;
        }

        log.info("Plan PDF cache miss for draft: {}, generating...", draftId);
        byte[] pdf = generatePlanPdf(draftId);

        redisTemplate.opsForValue().set(key, pdf, CACHE_TTL_HOURS, TimeUnit.HOURS);
        log.debug("Cached plan PDF for draft: {} (TTL: {}h)", draftId, CACHE_TTL_HOURS);

        return pdf;
    }

    /**
     * Removes the cached plan PDF for the given draft.
     *
     * <p>
     * Must be called after any change to plan data (QA rejection, re-submission)
     * to ensure the next request regenerates the PDF from current data.
     * </p>
     *
     * @param draftId ID of the draft whose cache entry should be removed
     */
    public void invalidateCache(Long draftId) {
        String key = CACHE_KEY_PREFIX + draftId;
        Boolean deleted = redisTemplate.delete(key);
        log.info("Invalidated plan PDF cache for draft: {} (entry existed: {})", draftId, deleted);
    }

    // =========================================================================
    // PDF CONSTRUCTION
    // =========================================================================

    private byte[] buildPdfBytes(ValidationDraft draft) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(baos));
             Document doc = new Document(pdfDoc, PageSize.A4)) {

            doc.setMargins(36, 36, 54, 36);

            PdfFont bold   = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD,
                PdfEncodings.CP1250, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
            PdfFont normal = PdfFontFactory.createFont(StandardFonts.HELVETICA,
                PdfEncodings.CP1250, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);

            // ── Cover / title block ──────────────────────────────────────────
            addTitleBlock(doc, draft, bold, normal);

            // ── Section A: Plan details ──────────────────────────────────────
            addSectionA(doc, draft.getPlanData().getDetails(), bold, normal);

            // ── Section B: Mapping status ────────────────────────────────────
            addSectionB(doc, draft.getPlanData().getMappingInfo(), bold, normal);

            // ── Section C: Acceptance criteria ───────────────────────────────
            addSectionC(doc, draft.getPlanData().getAcceptanceCriteria(), bold, normal);

            // ── Section D: Sensor positions (from draft) ─────────────────────
            addSectionD(doc, draft, bold, normal);

            // ── Section E: CAPA procedures ───────────────────────────────────
            addSectionE(doc, draft.getPlanData().getDeviationProcedures(), bold, normal);

            // ── Section F: Signatures ────────────────────────────────────────
            addSectionF(doc, draft.getPlanData().getTechnikSignature(),
                draft.getPlanData().getQaApproval(), bold, normal);
        }

        log.debug("Built plan PDF: {} bytes for draft: {}", baos.size(), draft.getId());
        return baos.toByteArray();
    }

    // ── TITLE BLOCK ──────────────────────────────────────────────────────────

    private void addTitleBlock(Document doc, ValidationDraft draft, PdfFont bold, PdfFont normal) {
        doc.add(new Paragraph("PLAN WALIDACJI OKRESOWEJ")
            .setFont(bold).setFontSize(16)
            .setFontColor(ColorConstants.WHITE)
            .setTextAlignment(TextAlignment.CENTER)
            .setBackgroundColor(CLR_TITLE)
            .setPaddingTop(12).setPaddingBottom(12)
            .setMarginBottom(4));

        doc.add(new Paragraph("PERIODIC REVALIDATION PLAN")
            .setFont(normal).setFontSize(10)
            .setFontColor(ColorConstants.WHITE)
            .setTextAlignment(TextAlignment.CENTER)
            .setBackgroundColor(CLR_SECTION)
            .setPaddingTop(4).setPaddingBottom(4)
            .setMarginBottom(12));

        CoolingDevice device = draft.getCoolingDevice();

        // Meta-data table
        Table meta = new Table(UnitValue.createPercentArray(new float[]{35, 65}))
            .setWidth(UnitValue.createPercentValue(100))
            .setMarginBottom(16);

        addMetaRow(meta, "Urządzenie:", device.getName(), bold, normal);
        addMetaRow(meta, "Nr inwentarzowy:", device.getInventoryNumber(), bold, normal);
        if (device.getLaboratory() != null) {
            addMetaRow(meta, "Pracownia:", device.getLaboratory().getFullName(), bold, normal);
        }
        addMetaRow(meta, "Typ procedury:", "Rewalidacja Okresowa (GMP Annex 15 §10)", bold, normal);
        addMetaRow(meta, "Nr szkicu:", String.valueOf(draft.getId()), bold, normal);
        addMetaRow(meta, "Autor planu:", draft.getCreatedBy(), bold, normal);
        addMetaRow(meta, "Data utworzenia:",
            draft.getCreatedAt() != null ? draft.getCreatedAt().format(DT_FMT) : "-",
            bold, normal);

        doc.add(meta);
    }

    // ── SECTION A: PLAN DETAILS ───────────────────────────────────────────────

    private void addSectionA(Document doc, PlanDetails details, PdfFont bold, PdfFont normal) {
        addSectionHeader(doc, "A. Szczegóły Planu Walidacji", bold);

        if (details == null) {
            doc.add(new Paragraph("Brak danych planu.").setFont(normal).setFontSize(9));
            return;
        }

        Table t = new Table(UnitValue.createPercentArray(new float[]{30, 70}))
            .setWidth(UnitValue.createPercentValue(100))
            .setMarginBottom(12);

        addMetaRow(t, "Uzasadnienie rewalidacji:",
            notNull(details.getRevalidationReason()), bold, normal);
        addMetaRow(t, "Data poprzedniej walidacji:",
            details.getPreviousValidationDate() != null
                ? details.getPreviousValidationDate().format(DATE_FMT) : "Nie podano",
            bold, normal);
        addMetaRow(t, "Nr sprawozdania poprzedniego:",
            notNull(details.getPreviousValidationNumber()), bold, normal);

        doc.add(t);
    }

    // ── SECTION B: MAPPING STATUS ─────────────────────────────────────────────

    private void addSectionB(Document doc, MappingInfo mappingInfo, PdfFont bold, PdfFont normal) {
        addSectionHeader(doc, "B. Status Mapowania Temperatury", bold);

        if (mappingInfo == null || mappingInfo.getMappingStatus() == null) {
            doc.add(new Paragraph("Status mapowania nie został sprawdzony.")
                .setFont(normal).setFontSize(9).setMarginBottom(12));
            return;
        }

        Table t = new Table(UnitValue.createPercentArray(new float[]{30, 70}))
            .setWidth(UnitValue.createPercentValue(100))
            .setMarginBottom(12);

        String statusDisplay = mappingInfo.getMappingStatus().name();
        if (mappingInfo.getMappingStatus() != com.mac.bry.validationsystem.wizard.MappingStatus.CURRENT
            && mappingInfo.getLastMappingDateManual() != null) {
            statusDisplay = "MAPOWANIE ZEWNĘTRZNE (Manualne)";
        }

        addMetaRow(t, "Data sprawdzenia:",
            mappingInfo.getMappingCheckDate() != null
                ? mappingInfo.getMappingCheckDate().format(DATE_FMT) : "-",
            bold, normal);
        addMetaRow(t, "Status mapowania:", statusDisplay, bold, normal);
        addMetaRow(t, "Potwierdzenie odchylenia:",
            Boolean.TRUE.equals(mappingInfo.getMappingOverdueAcknowledged())
                ? "TAK — technik potwierdził" : "N/D",
            bold, normal);

        // Manual mapping data (Annex 15 compliance for external mappings)
        if (mappingInfo.getMappingStatus() != com.mac.bry.validationsystem.wizard.MappingStatus.CURRENT
            && mappingInfo.getLastMappingDateManual() != null) {
            
            addMetaRow(t, "Data mapowania (ręcznie):", 
                mappingInfo.getLastMappingDateManual().format(DATE_FMT), bold, normal);
            addMetaRow(t, "Nr protokołu (ręcznie):", 
                notNull(mappingInfo.getMappingProtocolNumberManual()), bold, normal);
            addMetaRow(t, "Ważność mapowania (ręcznie):", 
                mappingInfo.getMappingValidUntilManual() != null 
                    ? mappingInfo.getMappingValidUntilManual().format(DATE_FMT) : "-", bold, normal);
        }

        doc.add(t);
    }

    // ── SECTION C: ACCEPTANCE CRITERIA ───────────────────────────────────────

    private void addSectionC(Document doc, PlanAcceptanceCriteria criteria,
                              PdfFont bold, PdfFont normal) {
        addSectionHeader(doc, "C. Kryteria Akceptacji i Stan Obciążenia", bold);

        if (criteria == null) {
            doc.add(new Paragraph("Kryteria akceptacji nie zostały zdefiniowane.")
                .setFont(normal).setFontSize(9).setMarginBottom(12));
            return;
        }

        Table t = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
            .setWidth(UnitValue.createPercentValue(100))
            .setMarginBottom(12);

        addMetaRow(t, "Stan obciążenia urządzenia:",
            notNull(criteria.getPlanDeviceLoadState()), bold, normal);
        addMetaRow(t, "Temperatura nominalna [°C]:",
            formatDouble(criteria.getPlanNominalTemp()), bold, normal);
        addMetaRow(t, "Min. temp. akceptacji [°C]:",
            formatDouble(criteria.getPlanAcceptanceTempMin()), bold, normal);
        addMetaRow(t, "Max. temp. akceptacji [°C]:",
            formatDouble(criteria.getPlanAcceptanceTempMax()), bold, normal);
        addMetaRow(t, "Max. MKT [°C]:",
            formatDouble(criteria.getPlanMktMaxTemp()), bold, normal);
        addMetaRow(t, "Max. delta jednorodności [°C]:",
            formatDouble(criteria.getPlanUniformityDeltaMax()), bold, normal);
        addMetaRow(t, "Max. dryft na rejestrator [°C]:",
            formatDouble(criteria.getPlanDriftMaxTemp()), bold, normal);

        doc.add(t);
    }

    // ── SECTION D: SENSOR POSITIONS ──────────────────────────────────────────

    private void addSectionD(Document doc, ValidationDraft draft, PdfFont bold, PdfFont normal) {
        addSectionHeader(doc, "D. Pozycje Czujników", bold);

        Table t = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
            .setWidth(UnitValue.createPercentValue(100))
            .setMarginBottom(12);

        // We remove reference sensor position, load state and series count as requested
        // because these belong to validation phases, not the plan / mapping summary.

        // Manual sensor positions summary (if no system mapping)
        MappingInfo mInfo = draft.getPlanData() != null ? draft.getPlanData().getMappingInfo() : null;
        if (mInfo != null && mInfo.getMappingStatus() != com.mac.bry.validationsystem.wizard.MappingStatus.CURRENT
            && (mInfo.getSensorCountManual() != null || mInfo.getControllerSensorLocationManual() != null)) {

            addMetaRow(t, "Liczba czujników (mapowanie zewnętrzne):",
                mInfo.getSensorCountManual() != null ? String.valueOf(mInfo.getSensorCountManual()) : "-", bold, normal);
            addMetaRow(t, "Lokalizacja czujnika kontrolnego:",
                formatPosition(mInfo.getControllerSensorLocationManual()), bold, normal);
            addMetaRow(t, "Lokalizacja Hot-Spot:",
                formatPosition(mInfo.getHotSpotLocationManual()), bold, normal);
            addMetaRow(t, "Lokalizacja Cold-Spot:",
                formatPosition(mInfo.getColdSpotLocationManual()), bold, normal);
        }

        doc.add(t);
    }

    // ── SECTION E: CAPA PROCEDURES ───────────────────────────────────────────

    private void addSectionE(Document doc, DeviationProcedures procedures,
                              PdfFont bold, PdfFont normal) {
        addSectionHeader(doc, "E. Procedury CAPA (Działania Korygująco-Zapobiegawcze)", bold);

        if (procedures == null) {
            doc.add(new Paragraph("Procedury CAPA nie zostały zdefiniowane.")
                .setFont(normal).setFontSize(9).setMarginBottom(12));
            return;
        }

        addCapaEntry(doc, "Odchylenie KRYTYCZNE:", procedures.getCriticalText(),
            CLR_DANGER, bold, normal);
        addCapaEntry(doc, "Odchylenie POWAŻNE:", procedures.getMajorText(),
            CLR_WARN, bold, normal);
        addCapaEntry(doc, "Odchylenie MNIEJSZE:", procedures.getMinorText(),
            CLR_OK, bold, normal);
    }

    private void addCapaEntry(Document doc, String label, String text,
                               DeviceRgb labelColor, PdfFont bold, PdfFont normal) {
        doc.add(new Paragraph(label)
            .setFont(bold).setFontSize(9)
            .setFontColor(labelColor)
            .setMarginTop(4).setMarginBottom(2));

        doc.add(new Paragraph(notNull(text))
            .setFont(normal).setFontSize(9)
            .setBorder(new SolidBorder(CLR_HDR, BORDER))
            .setPadding(4)
            .setMarginBottom(8));
    }

    // ── SECTION F: SIGNATURES ────────────────────────────────────────────────

    private void addSectionF(Document doc, TechnikSignature technikSig,
                              QaApprovalPath qaApproval, PdfFont bold, PdfFont normal) {
        addSectionHeader(doc, "F. Podpisy / Signatures", bold);

        // Technician signature
        doc.add(new Paragraph("Podpis Technologa (Faza 1 — Plan):")
            .setFont(bold).setFontSize(10).setMarginBottom(4));

        Table techTable = new Table(UnitValue.createPercentArray(new float[]{40, 60}))
            .setWidth(UnitValue.createPercentValue(100))
            .setMarginBottom(16);

        if (technikSig != null && technikSig.isSigned()) {
            addMetaRow(techTable, "Podpisano przez:", notNull(technikSig.getUsername()), bold, normal);
            addMetaRow(techTable, "Imię i nazwisko:", notNull(technikSig.getFullName()), bold, normal);
            addMetaRow(techTable, "Data podpisu:",
                technikSig.getSignedAt() != null ? technikSig.getSignedAt().format(DT_FMT) : "-",
                bold, normal);
        } else {
            addMetaRow(techTable, "Status:", "Oczekuje na podpis technologa", bold, normal);
        }
        doc.add(techTable);

        // QA signature
        doc.add(new Paragraph("Podpis QA (Zatwierdzenie):")
            .setFont(bold).setFontSize(10).setMarginBottom(4));

        Table qaTable = new Table(UnitValue.createPercentArray(new float[]{40, 60}))
            .setWidth(UnitValue.createPercentValue(100))
            .setMarginBottom(16);

        if (qaApproval != null && qaApproval.isApproved()) {
            addMetaRow(qaTable, "Zatwierdzone przez:", notNull(qaApproval.getElectronicUsername()), bold, normal);
            addMetaRow(qaTable, "Imię i nazwisko:", notNull(qaApproval.getElectronicFullName()), bold, normal);
            addMetaRow(qaTable, "Data zatwierdzenia:",
                qaApproval.getElectronicSignedAt() != null
                    ? qaApproval.getElectronicSignedAt().format(DT_FMT) : "-",
                bold, normal);
        } else {
            addMetaRow(qaTable, "Status:", "Oczekuje na zatwierdzenie QA / Awaiting QA Approval", bold, normal);
            
            // Add wet signature field for external QA
            qaTable.addCell(new com.itextpdf.layout.element.Cell(1, 2)
                .add(new com.itextpdf.layout.element.Paragraph("\n\n...........................................................................\n(Data i podpis QA / Date and signature)")
                    .setFont(normal).setFontSize(8).setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER))
                .setBorder(new com.itextpdf.layout.borders.SolidBorder(com.itextpdf.kernel.colors.ColorConstants.LIGHT_GRAY, BORDER))
                .setPadding(10));
        }
        doc.add(qaTable);
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private void addSectionHeader(Document doc, String title, PdfFont bold) {
        doc.add(new Paragraph(title)
            .setFont(bold).setFontSize(11)
            .setFontColor(ColorConstants.WHITE)
            .setBackgroundColor(CLR_SECTION)
            .setPaddingTop(6).setPaddingBottom(6).setPaddingLeft(8)
            .setMarginTop(12).setMarginBottom(6));
    }

    private void addMetaRow(Table table, String label, String value,
                             PdfFont bold, PdfFont normal) {
        table.addCell(new Cell()
            .add(new Paragraph(label).setFont(bold).setFontSize(9))
            .setBackgroundColor(CLR_META_LBL)
            .setBorder(new SolidBorder(ColorConstants.LIGHT_GRAY, BORDER))
            .setPadding(4));

        table.addCell(new Cell()
            .add(new Paragraph(value).setFont(normal).setFontSize(9))
            .setBorder(new SolidBorder(ColorConstants.LIGHT_GRAY, BORDER))
            .setPadding(4));
    }

    private String notNull(String value) {
        return value != null ? value : "-";
    }

    private String formatDouble(Double value) {
        return value != null ? String.valueOf(value) : "-";
    }

    private String formatPosition(RecorderPosition pos) {
        if (pos == null) return "-";
        
        String name = pos.name();
        // Convert enum like TOP_REAR_LEFT to "GÓRA - TYŁ - LEWO"
        String result = name
            .replace("TOP", "GÓRA")
            .replace("MIDDLE", "ŚRODEK")
            .replace("BOTTOM", "DÓŁ")
            .replace("REAR", "TYŁ")
            .replace("CENTER", "ŚRODEK")
            .replace("FRONT", "PRZÓD")
            .replace("LEFT", "LEWO")
            .replace("RIGHT", "PRAWO")
            .replace("_", " - ");
            
        return result;
    }
}
