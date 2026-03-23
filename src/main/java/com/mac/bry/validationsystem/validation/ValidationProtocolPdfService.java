package com.mac.bry.validationsystem.validation;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Div;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.AreaBreakType;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.mac.bry.validationsystem.device.CoolingDevice;
import com.mac.bry.validationsystem.deviation.DeviationEvent;
import com.mac.bry.validationsystem.deviation.DeviationSeverity;
import com.mac.bry.validationsystem.measurement.MeasurementSeries;
import com.mac.bry.validationsystem.stats.ValidationSummaryStatsDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serwis generujący kompletny Protokół Walidacji w formacie PDF (iText 7).
 *
 * <p>
 * Zawiera wszystkie sekcje protokołu walidacyjnego:
 * </p>
 * <ul>
 * <li>Strona tytułowa + metadane urządzenia i sesji</li>
 * <li>Tabela A — Statystyki globalne temperatury</li>
 * <li>Tabela B — MKT (Mean Kinetic Temperature)</li>
 * <li>Tabela B2 — Wyniki per seria pomiarowa</li>
 * <li>Tabela C — Zgodność temperaturowa (Compliance)</li>
 * <li>Tabela D — Stabilność termiczna (Drift / Spike)</li>
 * <li>Tabela G — Analiza odchyleń (Deviation Analysis + CAPA)</li>
 * <li>Wnioski + data następnej walidacji</li>
 * </ul>
 *
 * <p>
 * Wzorzec taki sam jak {@link SchematWizualnyPdfService}.
 * </p>
 */
@Slf4j
@Service
public class ValidationProtocolPdfService {

        // ── Kolory ───────────────────────────────────────────────────────────────
        private static final DeviceRgb CLR_TITLE = new DeviceRgb(31, 73, 125);
        private static final DeviceRgb CLR_SECTION = new DeviceRgb(68, 114, 196);
        private static final DeviceRgb CLR_HDR = new DeviceRgb(189, 215, 238);
        private static final DeviceRgb CLR_META_LBL = new DeviceRgb(242, 242, 242);
        private static final DeviceRgb CLR_OK = new DeviceRgb(16, 185, 129);
        private static final DeviceRgb CLR_WARN = new DeviceRgb(245, 158, 11);
        private static final DeviceRgb CLR_DANGER = new DeviceRgb(220, 38, 38);
        private static final DeviceRgb CLR_PURPLE = new DeviceRgb(124, 58, 237);
        private static final DeviceRgb CLR_LIGHT_BG = new DeviceRgb(250, 251, 254);

        private static final float BORDER = 0.5f;
        private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
        private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        // ── API publiczne ─────────────────────────────────────────────────────────

        /**
         * Generuje kompletny Protokół Walidacji PDF.
         *
         * @param validation walidacja z seriami pomiarowymi i urządzeniem
         * @param stats      statystyki zbiorcze (sekcje A–D), null jeśli brak
         * @param deviations naruszenia (sekcja G), null/empty jeśli brak
         * @return bajty PDF
         */
        public byte[] generateProtocolPdf(Validation validation,
                        ValidationSummaryStatsDto stats,
                        List<DeviationEvent> deviations) throws IOException {
                log.info("Generowanie Protokołu Walidacji PDF dla walidacji ID: {}", validation.getId());

                CoolingDevice device = validation.getCoolingDevice();

                ByteArrayOutputStream baos = new ByteArrayOutputStream();

                try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(baos));
                                Document doc = new Document(pdfDoc, PageSize.A4)) {

                        doc.setMargins(36, 36, 36, 36);

                        PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD,
                                        PdfEncodings.CP1250, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
                        PdfFont normal = PdfFontFactory.createFont(StandardFonts.HELVETICA,
                                        PdfEncodings.CP1250, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);

                        // Dodaj numerację stron z poprawką dla niespójnej numeracji
                        PageNumberEventHandler pageHandler = new PageNumberEventHandler(normal);
                        pdfDoc.addEventHandler(PdfDocumentEvent.END_PAGE, pageHandler);

                        // ── 1. Strona tytułowa ──────────────────────────────────────────
                        addTitlePage(doc, validation, device, stats, normal, bold);

                        // ── 2. Kryteria akceptacji walidacji ───────────────────────────
                        addAcceptanceCriteria(doc, validation, device, normal, bold);

                        // ── 3. Sekcje statystyk (od nowej strony) ──────────────────────
                        if (stats != null) {
                                doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
                                addSectionA(doc, validation, stats, normal, bold);
                                addSectionB(doc, stats, normal, bold);
                        }

                        // ── 3. Wyniki per seria ────────────────────────────────────────
                        doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
                        addSeriesResults(doc, validation, normal, bold);

                        // ── 4. Compliance + Drift ─────────────────────────────────────
                        if (stats != null) {
                                addSectionC(doc, stats, normal, bold);
                                addSectionD(doc, stats, normal, bold);
                        }

                        // ── 5. Analiza odchyleń ────────────────────────────────────────
                        // Dodaj przestrzeń przed sekcją G dla lepszego layoutu
                        doc.add(new Paragraph().setMarginTop(20));
                        addSectionG(doc, deviations, normal, bold);

                        // ── 6. Wnioski ─────────────────────────────────────────────────
                        doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
                        addConclusions(doc, validation, device, stats, normal, bold);

                        // ── 7. Stopka ──────────────────────────────────────────────────
                        addFooter(doc, validation, normal);

                        // ── POPRAWKA: Wypełnij szablony numeracji stron poprawną liczbą ──
                        pageHandler.fillTemplates(pdfDoc);
                }

                log.info("Protokół Walidacji PDF gotowy: {} bajtów", baos.size());
                return baos.toByteArray();
        }

        // =========================================================================
        // STRONA TYTUŁOWA
        // =========================================================================

        private void addTitlePage(Document doc, Validation validation, CoolingDevice device,
                        ValidationSummaryStatsDto stats, PdfFont normal, PdfFont bold) {
                // Nagłówek
                doc.add(new Paragraph("PROTOKÓŁ WALIDACJI")
                                .setFont(bold).setFontSize(16)
                                .setFontColor(ColorConstants.WHITE)
                                .setTextAlignment(TextAlignment.CENTER)
                                .setBackgroundColor(CLR_TITLE)
                                .setPaddingTop(12).setPaddingBottom(12)
                                .setMarginBottom(4));

                doc.add(new Paragraph("WARUNKÓW PRZECHOWYWANIA")
                                .setFont(bold).setFontSize(12)
                                .setFontColor(ColorConstants.WHITE)
                                .setTextAlignment(TextAlignment.CENTER)
                                .setBackgroundColor(CLR_TITLE)
                                .setPaddingTop(6).setPaddingBottom(6)
                                .setMarginBottom(16));

                // RPW
                String rpw = safe(validation.getValidationPlanNumber());
                doc.add(new Paragraph("Nr RPW: " + rpw)
                                .setFont(bold).setFontSize(10)
                                .setTextAlignment(TextAlignment.CENTER)
                                .setMarginBottom(16));

                // Metryka urządzenia
                addSectionHeader(doc, "METRYKA URZĄDZENIA", bold);
                Table meta = metaTable();
                String lab = device.getLaboratory() != null ? device.getLaboratory().getFullName() : "–";
                String dept = device.getDepartment() != null ? device.getDepartment().getName() : "–";
                addMetaRow(meta, "Nazwa urządzenia:", safe(device.getName()), normal, bold);
                addMetaRow(meta, "Lokalizacja:", lab + " / " + dept, normal, bold);
                addMetaRow(meta, "Nr inwentarzowy:", safe(device.getInventoryNumber()), normal, bold);
                addMetaRow(meta, "Typ komory:", device.getChamberType() != null ? device.getChamberType().name() : "–",
                                normal,
                                bold);
                addMetaRow(meta, "Materiał:", safe(device.getMaterialName()), normal, bold);
                addMetaRow(meta, "Zakres temp. materiału:", safe(device.getMaterialTemperatureRange()), normal, bold);
                addMetaRow(meta, "Zakres operacyjny:", buildTempRange(device), normal, bold);
                addMetaRow(meta, "Objętość urządzenia:", safe(device.getFormattedVolume()), normal, bold);
                addMetaRow(meta, "Klasa kubatury (PDA TR-64):", safe(device.getVolumeCategoryDisplayName()), normal, bold);
                if (device.getMinMeasurementPoints() != null) {
                        addMetaRow(meta, "Min. punkty pomiarowe:", device.getMinMeasurementPoints() + " punktów", normal, bold);
                }
                doc.add(meta);

                // Metadane sesji
                if (stats != null) {
                        addSectionHeader(doc, "METADANE SESJI WALIDACYJNEJ", bold);
                        Table session = metaTable();
                        addMetaRow(session, "Początek walidacji:",
                                        stats.getValidationStartTime() != null
                                                        ? stats.getValidationStartTime().format(DT_FMT)
                                                        : "–",
                                        normal, bold);
                        addMetaRow(session, "Koniec walidacji:",
                                        stats.getValidationEndTime() != null
                                                        ? stats.getValidationEndTime().format(DT_FMT)
                                                        : "–",
                                        normal, bold);
                        addMetaRow(session, "Czas trwania:", stats.getFormattedDuration(), normal, bold);
                        addMetaRow(session, "Interwał pomiarowy:",
                                        stats.getDominantIntervalMinutes() != null
                                                        ? stats.getDominantIntervalMinutes() + " min"
                                                        : "–",
                                        normal, bold);
                        addMetaRow(session, "Liczba serii (siatka / ref.):",
                                        String.format("%d (%d siatka + %d ref.)",
                                                        nn(stats.getTotalSeriesCount()), nn(stats.getGridSeriesCount()),
                                                        nn(stats.getReferenceSeriesCount())),
                                        normal, bold);
                        addMetaRow(session, "Łączna liczba pomiarów:",
                                        stats.getTotalMeasurementCount() != null
                                                        ? stats.getTotalMeasurementCount().toString()
                                                        : "–",
                                        normal, bold);
                        addMetaRow(session, "Stan urządzenia podczas walidacji:",
                                        validation.getDeviceLoadState() != null
                                                        ? validation.getDeviceLoadState().getFullDescription()
                                                        : "nieokreślony",
                                        normal, bold);
                        doc.add(session);
                }

                // Status walidacji
                doc.add(new Paragraph("Status: " + validation.getStatus().getDisplayName())
                                .setFont(bold).setFontSize(10)
                                .setTextAlignment(TextAlignment.CENTER)
                                .setMarginTop(16));
        }

        // =========================================================================
        // TABELA A — Statystyki globalne temperatury
        // =========================================================================

        private void addSectionA(Document doc, Validation validation, ValidationSummaryStatsDto s, PdfFont normal, PdfFont bold) {
                addSectionHeader(doc, "TABELA A — STATYSTYKI GLOBALNE TEMPERATURY", bold);

                Table t = new Table(UnitValue.createPercentArray(new float[] { 8, 42, 25, 25 }))
                                .setWidth(UnitValue.createPercentValue(100)).setMarginBottom(14)
                                .setKeepTogether(true); // Nie łam tabeli między stronami
                addTableHeader(t, new String[] { "Nr", "Parametr", "Wartość", "Jednostka" }, bold);

                addStatRow(t, "A.1", "Min. temperatura globalna (coldspot)", s.getFormattedGlobalMinTemp(), "°C",
                                normal);
                addStatRow(t, "A.2", "Max. temperatura globalna (hotspot)", s.getFormattedGlobalMaxTemp(), "°C",
                                normal);
                addStatRow(t, "A.3", "Średnia temperatura ważona", s.getFormattedOverallAvgTemp(), "°C", normal);
                addStatRow(t, "A.4", "Odchylenie standardowe (pooled)", s.getFormattedGlobalStdDev(), "°C", normal);
                addStatRow(t, "A.5", "Współczynnik zmienności CV", s.getFormattedGlobalCvPercentage(), "%", normal);
                addStatRow(t, "A.6", "Hotspot (najwyższa średnia)", s.getFormattedHotspotTemp(), "°C", normal);
                addSpatialInfoRow(t, "A.6.1", "Położenie hotspot", getHotspotLocation(validation, s), "", normal);
                addStatRow(t, "A.7", "Coldspot (najniższa średnia)", s.getFormattedColdspotTemp(), "°C", normal);
                addSpatialInfoRow(t, "A.7.1", "Położenie coldspot", getColdspotLocation(validation, s), "", normal);
                addSpatialInfoRow(t, "A.8", "Czujnik kontrolujący", getControlSensorInfo(validation), "", normal);
                addStatRow(t, "A.8", "Niepewność rozszerzona U (k=2)", s.getFormattedGlobalExpandedUncertainty(), "°C",
                                normal);
                addStatRow(t, "A.9", "Percentyl P5 / P95",
                                s.getFormattedPercentile5() + " / " + s.getFormattedPercentile95(), "°C", normal);

                doc.add(t);
        }

        // =========================================================================
        // TABELA B — MKT (Mean Kinetic Temperature)
        // =========================================================================

        private void addSectionB(Document doc, ValidationSummaryStatsDto s, PdfFont normal, PdfFont bold) {
                addSectionHeader(doc, "TABELA B — MEAN KINETIC TEMPERATURE (MKT)", bold);

                Table t = new Table(UnitValue.createPercentArray(new float[] { 8, 42, 25, 25 }))
                                .setWidth(UnitValue.createPercentValue(100)).setMarginBottom(14)
                                .setKeepTogether(true); // Nie łam tabeli między stronami
                addTableHeader(t, new String[] { "Nr", "Parametr", "Wartość", "Jednostka" }, bold);

                addStatRow(t, "B.1", "MKT globalny (Arrhenius)", s.getFormattedGlobalMkt(), "°C", normal);
                addStatRow(t, "B.2", "ΔH/R (energia aktywacji)", s.getFormattedDeltaHR(), "K", normal);
                addStatRow(t, "B.3", "MKT worst case (max. seria)", s.getFormattedMktWorstValue(), "°C", normal);
                addStatRow(t, "B.4", "MKT best case (min. seria)", s.getFormattedMktBestValue(), "°C", normal);
                addStatRow(t, "B.5", "MKT rejestrator referencyjny", s.getFormattedMktReferenceValue(), "°C", normal);
                addStatRow(t, "B.6", "Δ MKT (wew. vs ref.)", s.getFormattedMktDelta(), "°C", normal);

                doc.add(t);
        }

        // =========================================================================
        // WYNIKI PER SERIA POMIAROWA
        // =========================================================================

        private void addSeriesResults(Document doc, Validation validation, PdfFont normal, PdfFont bold) {
                // Podział na serie siatki (urządzenie) vs referencyjne (otoczenie)
                List<MeasurementSeries> gridSeries = validation.getMeasurementSeries().stream()
                                .filter(s -> Boolean.FALSE.equals(s.getIsReferenceRecorder()))
                                .toList();

                List<MeasurementSeries> refSeries = validation.getMeasurementSeries().stream()
                                .filter(s -> Boolean.TRUE.equals(s.getIsReferenceRecorder()))
                                .toList();

                // === TABELA SERII SIATKI (urządzenie) ===
                addSectionHeader(doc, "WYNIKI POMIARÓW — SERIE SIATKI (URZĄDZENIE)", bold);

                Table t = new Table(UnitValue.createPercentArray(new float[] { 5, 12, 18, 13, 13, 13, 13, 13 }))
                                .setWidth(UnitValue.createPercentValue(100)).setMarginBottom(14).setFontSize(7.5f);
                addTableHeader(t,
                                new String[] { "LP", "S/N", "Pozycja", "T.Min", "T.Max", "T.Śr", "Rozstęp",
                                                "Naruszenia" },
                                bold);

                int lp = 1;
                for (MeasurementSeries s : gridSeries) {
                        String pos = s.getRecorderPosition() != null ? s.getRecorderPosition().getDisplayName() : "–";
                        String sn = safe(s.getRecorderSerialNumber());
                        String min = fmtTemp(s.getMinTemperature());
                        String max = fmtTemp(s.getMaxTemperature());
                        String avg = fmtTemp(s.getAvgTemperature());
                        double range = (s.getMaxTemperature() != null && s.getMinTemperature() != null)
                                        ? s.getMaxTemperature() - s.getMinTemperature()
                                        : 0;
                        String rangeStr = String.format("%.2f", range);
                        String viol = s.getViolationCount() != null ? s.getViolationCount().toString() : "0";

                        Cell[] cells = new Cell[] {
                                        dataCell(String.valueOf(lp++), normal, TextAlignment.CENTER),
                                        dataCell(sn, normal, TextAlignment.LEFT),
                                        dataCell(pos, normal, TextAlignment.LEFT),
                                        dataCell(min, normal, TextAlignment.CENTER),
                                        dataCell(max, normal, TextAlignment.CENTER),
                                        dataCell(avg, normal, TextAlignment.CENTER),
                                        dataCell(rangeStr, normal, TextAlignment.CENTER),
                                        dataCell(viol, normal, TextAlignment.CENTER)
                        };

                        // Podświetl wiersz z naruszeniami
                        if (s.getViolationCount() != null && s.getViolationCount() > 0) {
                                for (Cell c : cells) {
                                        c.setBackgroundColor(new DeviceRgb(254, 242, 242));
                                }
                        }

                        for (Cell c : cells)
                                t.addCell(c);
                }

                // Średnia urządzenia
                Double avgDevice = validation.getAverageDeviceTemperature();
                Cell labelCell = new Cell(1, 3)
                                .add(new Paragraph("Średnia temperatura urządzenia:").setFont(bold).setFontSize(8))
                                .setBorder(new SolidBorder(BORDER)).setPadding(4)
                                .setBackgroundColor(CLR_HDR);
                t.addCell(labelCell);

                Cell valCell = new Cell(1, 5)
                                .add(new Paragraph(avgDevice != null ? String.format("%.2f°C", avgDevice) : "–")
                                                .setFont(bold).setFontSize(8).setTextAlignment(TextAlignment.CENTER))
                                .setBorder(new SolidBorder(BORDER)).setPadding(4)
                                .setBackgroundColor(CLR_HDR);
                t.addCell(valCell);

                doc.add(t);

                // === TABELA REJESTRATORÓW REFERENCYJNYCH (otoczenie) ===
                if (!refSeries.isEmpty()) {
                        addSectionHeader(doc, "REJESTRATORY REFERENCYJNE (OTOCZENIE)", bold);

                        Table refTable = new Table(UnitValue.createPercentArray(new float[] { 5, 15, 15, 15, 15, 15, 20 }))
                                        .setWidth(UnitValue.createPercentValue(100)).setMarginBottom(14).setFontSize(7.5f);
                        addTableHeader(refTable,
                                        new String[] { "LP", "S/N", "T.Min", "T.Max", "T.Śr", "Rozstęp", "Uwagi" },
                                        bold);

                        int refLp = 1;
                        for (MeasurementSeries s : refSeries) {
                                String sn = safe(s.getRecorderSerialNumber());
                                String min = fmtTemp(s.getMinTemperature());
                                String max = fmtTemp(s.getMaxTemperature());
                                String avg = fmtTemp(s.getAvgTemperature());
                                double range = (s.getMaxTemperature() != null && s.getMinTemperature() != null)
                                                ? s.getMaxTemperature() - s.getMinTemperature()
                                                : 0;
                                String rangeStr = String.format("%.2f", range);
                                String notes = "Temperatura otoczenia";

                                Cell[] cells = new Cell[] {
                                                dataCell(String.valueOf(refLp++), normal, TextAlignment.CENTER),
                                                dataCell(sn, normal, TextAlignment.LEFT),
                                                dataCell(min, normal, TextAlignment.CENTER),
                                                dataCell(max, normal, TextAlignment.CENTER),
                                                dataCell(avg, normal, TextAlignment.CENTER),
                                                dataCell(rangeStr, normal, TextAlignment.CENTER),
                                                dataCell(notes, normal, TextAlignment.LEFT)
                                };

                                // Podświetl tło jako informacyjne (nie błędy)
                                for (Cell c : cells) {
                                        c.setBackgroundColor(new DeviceRgb(248, 250, 252)); // jasno-szare tło
                                }

                                for (Cell c : cells)
                                        refTable.addCell(c);
                        }

                        doc.add(refTable);
                }
        }

        // =========================================================================
        // TABELA C — Zgodność temperaturowa (Compliance)
        // =========================================================================

        private void addSectionC(Document doc, ValidationSummaryStatsDto s, PdfFont normal, PdfFont bold) {
                addSectionHeader(doc, "TABELA C — ZGODNOŚĆ TEMPERATUROWA (COMPLIANCE)", bold);

                Table t = new Table(UnitValue.createPercentArray(new float[] { 8, 42, 25, 25 }))
                                .setWidth(UnitValue.createPercentValue(100)).setMarginBottom(14)
                                .setKeepTogether(true); // Nie łam tabeli między stronami
                addTableHeader(t, new String[] { "Nr", "Parametr", "Wartość", "Jednostka" }, bold);

                addStatRow(t, "C.1", "Łączny czas w zakresie", s.getFormattedTotalTimeIn(), "min", normal);
                addStatRow(t, "C.2", "Łączny czas poza zakresem", s.getFormattedTotalTimeOut(), "min", normal);
                addStatRow(t, "C.3", "Globalny wskaźnik zgodności", s.getFormattedCompliancePercentage(), "%", normal);
                addStatRow(t, "C.4", "Łączna liczba przekroczeń",
                                s.getTotalViolations() != null ? s.getTotalViolations().toString() : "0", "szt.",
                                normal);
                addStatRow(t, "C.5", "Najdłuższe pojedyncze przekroczenie", s.getFormattedMaxViolationDuration(), "min",
                                normal);
                addStatRow(t, "C.6", "Serie siatki z przekroczeniami",
                                s.getSeriesWithViolationsCount() != null
                                                ? s.getSeriesWithViolationsCount() + " / " + nn(s.getGridSeriesCount())
                                                : "–",
                                "szt.", normal);
                addStatRow(t, "C.7", "Serie siatki w pełni zgodne",
                                s.getSeriesFullyCompliantCount() != null
                                                ? s.getSeriesFullyCompliantCount() + " / " + nn(s.getGridSeriesCount())
                                                : "–",
                                "szt.", normal);

                doc.add(t);
        }

        // =========================================================================
        // TABELA D — Stabilność termiczna (Drift / Spike)
        // =========================================================================

        private void addSectionD(Document doc, ValidationSummaryStatsDto s, PdfFont normal, PdfFont bold) {
                addSectionHeader(doc, "TABELA D — STABILNOŚĆ TERMICZNA (DRIFT / SPIKE)", bold);

                Table t = new Table(UnitValue.createPercentArray(new float[] { 8, 42, 25, 25 }))
                                .setWidth(UnitValue.createPercentValue(100)).setMarginBottom(14)
                                .setKeepTogether(true); // Nie łam tabeli między stronami
                addTableHeader(t, new String[] { "Nr", "Parametr", "Wartość", "Jednostka" }, bold);

                addStatRow(t, "D.1", "Max. współczynnik driftu (worst case)", s.getFormattedMaxAbsTrend(), "°C/h",
                                normal);
                addStatRow(t, "D.2", "Średniow. współczynnik trendu", s.getFormattedAvgTrend(), "°C/h", normal);
                addStatRow(t, "D.3", "Łączna liczba spike'ów",
                                s.getTotalSpikeCount() != null ? s.getTotalSpikeCount().toString() : "0", "szt.",
                                normal);
                addStatRow(t, "D.4", "Serie z driftem (DRIFT/MIXED)",
                                s.getSeriesWithDriftCount() != null ? s.getSeriesWithDriftCount().toString() : "0",
                                "szt.", normal);
                addStatRow(t, "D.5", "Serie stabilne (STABLE)",
                                s.getSeriesStableCount() != null ? s.getSeriesStableCount().toString() : "0", "szt.",
                                normal);
                addStatRow(t, "D.6", "Dominująca klasyfikacja", s.getFormattedDominantClassification(), "–", normal);

                doc.add(t);
        }

        // =========================================================================
        // TABELA G — Analiza odchyleń (Deviation Analysis)
        // =========================================================================

        private void addSectionG(Document doc, List<DeviationEvent> deviations, PdfFont normal, PdfFont bold) {
                addSectionHeader(doc, "TABELA G — ANALIZA ODCHYLEŃ (DEVIATION ANALYSIS)", bold);

                if (deviations == null || deviations.isEmpty()) {
                        doc.add(new Paragraph("✓ WYNIK POZYTYWNY — BRAK ODCHYLEŃ TEMPERATUROWYCH")
                                        .setFont(bold).setFontSize(11)
                                        .setFontColor(CLR_OK)
                                        .setTextAlignment(TextAlignment.CENTER)
                                        .setMarginBottom(16));

                        doc.add(new Paragraph("Wszystkie serie pomiarowe utrzymywały temperaturę w zdefiniowanych granicach przez cały okres walidacji.")
                                        .setFont(normal).setFontSize(9)
                                        .setTextAlignment(TextAlignment.CENTER)
                                        .setMarginBottom(20));

                        // Tabela podsumowująca zgodność
                        Table summaryTable = new Table(UnitValue.createPercentArray(new float[] { 40, 20, 40 }))
                                        .setWidth(UnitValue.createPercentValue(80))
                                        .setMarginBottom(20)
                                        .setKeepTogether(true);

                        summaryTable.addHeaderCell(createHeaderCell("Parametr", bold));
                        summaryTable.addHeaderCell(createHeaderCell("Status", bold));
                        summaryTable.addHeaderCell(createHeaderCell("Uwagi", bold));

                        summaryTable.addCell(createDataCell("Naruszenia temperaturowe", normal, TextAlignment.LEFT));
                        summaryTable.addCell(createDataCell("0", normal, TextAlignment.CENTER)
                                        .setBackgroundColor(CLR_OK).setFontColor(ColorConstants.WHITE).setBold());
                        summaryTable.addCell(createDataCell("Pełna zgodność z wymaganiami", normal, TextAlignment.LEFT));

                        summaryTable.addCell(createDataCell("Czas poza zakresem", normal, TextAlignment.LEFT));
                        summaryTable.addCell(createDataCell("0 min", normal, TextAlignment.CENTER)
                                        .setBackgroundColor(CLR_OK).setFontColor(ColorConstants.WHITE).setBold());
                        summaryTable.addCell(createDataCell("100% czasu w zakresie", normal, TextAlignment.LEFT));

                        summaryTable.addCell(createDataCell("Wymagana analiza CAPA", normal, TextAlignment.LEFT));
                        summaryTable.addCell(createDataCell("NIE", normal, TextAlignment.CENTER)
                                        .setBackgroundColor(CLR_OK).setFontColor(ColorConstants.WHITE).setBold());
                        summaryTable.addCell(createDataCell("Brak naruszeń do analizy", normal, TextAlignment.LEFT));

                        doc.add(new Paragraph().add(summaryTable).setTextAlignment(TextAlignment.CENTER));

                        doc.add(new Paragraph("Zgodnie z wymogami GMP wszystkie serie pomiarowe zostały ocenione jako zgodne. " +
                                            "Nie wykryto odchyleń wymagających analizy przyczyn podstawowych (Root Cause Analysis) " +
                                            "ani działań naprawczych i zapobiegawczych (CAPA).")
                                        .setFont(normal).setFontSize(9)
                                        .setMarginTop(10)
                                        .setMarginBottom(20));
                        return;
                }

                // Podsumowanie severity
                long critical = deviations.stream().filter(d -> d.getSeverity() == DeviationSeverity.CRITICAL).count();
                long major = deviations.stream().filter(d -> d.getSeverity() == DeviationSeverity.MAJOR).count();
                long minor = deviations.stream().filter(d -> d.getSeverity() == DeviationSeverity.MINOR).count();

                String summary = String.format("Wykryto %d odchyleń: %d CRITICAL, %d MAJOR, %d MINOR",
                                deviations.size(), critical, major, minor);
                doc.add(new Paragraph(summary)
                                .setFont(bold).setFontSize(9)
                                .setMarginBottom(8));

                // Tabela naruszeń
                Table t = new Table(UnitValue.createPercentArray(new float[] { 5, 10, 16, 12, 12, 10, 10, 10, 15 }))
                                .setWidth(UnitValue.createPercentValue(100)).setMarginBottom(6).setFontSize(7f);
                addTableHeader(t, new String[] { "#", "Severity", "Pozycja", "Początek", "Koniec",
                                "Czas trw.", "T.Szczyt", "Granica", "ΔT" }, bold);

                int idx = 1;
                for (DeviationEvent dev : deviations) {
                        DeviceRgb rowColor = getDeviationRowColor(dev.getSeverity());

                        t.addCell(dataCell(String.valueOf(idx++), normal, TextAlignment.CENTER));
                        Cell sevCell = dataCell(dev.getSeverity().getDisplayName(), bold, TextAlignment.CENTER);
                        sevCell.setFontColor(ColorConstants.WHITE).setBackgroundColor(rowColor);
                        t.addCell(sevCell);
                        t.addCell(dataCell(dev.getSeriesPositionName(), normal, TextAlignment.LEFT));
                        t.addCell(dataCell(dev.getStartTime() != null ? dev.getStartTime().format(DT_FMT) : "–", normal,
                                        TextAlignment.CENTER));
                        t.addCell(dataCell(dev.getEndTime() != null ? dev.getEndTime().format(DT_FMT) : "–", normal,
                                        TextAlignment.CENTER));
                        t.addCell(dataCell(dev.getFormattedDuration(), normal, TextAlignment.CENTER));
                        t.addCell(dataCell(dev.getFormattedPeakTemp(), normal, TextAlignment.CENTER));
                        t.addCell(dataCell(dev.getFormattedViolatedLimit(), normal, TextAlignment.CENTER));
                        t.addCell(dataCell(dev.getFormattedDeltaTemp(), normal, TextAlignment.CENTER));
                }
                doc.add(t);

                // Dokumentacja CAPA per naruszenie (jeśli wypełnione)
                boolean hasAnyAnalysis = deviations.stream().anyMatch(d -> d.getAnalysis() != null);
                if (hasAnyAnalysis) {
                        doc.add(new Paragraph("DOKUMENTACJA CAPA")
                                        .setFont(bold).setFontSize(9)
                                        .setFontColor(CLR_SECTION)
                                        .setMarginTop(10).setMarginBottom(6));

                        int capaIdx = 1;
                        for (DeviationEvent dev : deviations) {
                                if (dev.getAnalysis() == null)
                                        continue;

                                Div capaBlock = new Div()
                                                .setMarginBottom(8)
                                                .setBackgroundColor(CLR_LIGHT_BG)
                                                .setPadding(8)
                                                .setBorderLeft(new SolidBorder(getDeviationRowColor(dev.getSeverity()),
                                                                3));

                                capaBlock.add(new Paragraph(String.format("Naruszenie #%d — %s (%s)",
                                                capaIdx++, dev.getSeriesPositionName(),
                                                dev.getSeverity().getDisplayName()))
                                                .setFont(bold).setFontSize(8).setMarginBottom(4));

                                Table capaTable = metaTable();
                                addMetaRow(capaTable, "Przyczyna:", safe(dev.getAnalysis().getRootCause()), normal,
                                                bold);
                                addMetaRow(capaTable, "Wpływ na produkt:", safe(dev.getAnalysis().getProductImpact()),
                                                normal, bold);
                                addMetaRow(capaTable, "Działania korygujące:",
                                                safe(dev.getAnalysis().getCorrectiveAction()), normal,
                                                bold);
                                addMetaRow(capaTable, "Opisał:",
                                                safe(dev.getAnalysis().getAnalyzedBy())
                                                                + (dev.getAnalysis().getAnalyzedAt() != null
                                                                                ? " (" + dev.getAnalysis()
                                                                                                .getAnalyzedAt()
                                                                                                .format(DT_FMT) + ")"
                                                                                : ""),
                                                normal, bold);

                                capaBlock.add(capaTable);
                                doc.add(capaBlock);
                        }
                }
        }

        // =========================================================================
        // WNIOSKI
        // =========================================================================

        private void addConclusions(Document doc, Validation validation, CoolingDevice device,
                        ValidationSummaryStatsDto stats, PdfFont normal, PdfFont bold) {
                addSectionHeader(doc, "WNIOSKI I OCENA", bold);

                Double avgTemp = validation.getAverageDeviceTemperature();
                Double minOp = device.getMinOperatingTemp();
                Double maxOp = device.getMaxOperatingTemp();

                boolean deviceOk = avgTemp != null && minOp != null && maxOp != null
                                && avgTemp >= minOp && avgTemp <= maxOp;

                boolean complianceOk = stats != null
                                && stats.getGlobalCompliancePercentage() != null
                                && stats.getGlobalCompliancePercentage() >= 95.0;

                boolean allOk = deviceOk && complianceOk;

                // Wniosek
                Table concl = metaTable();
                if (allOk) {
                        addMetaRow(concl, "Ocena:",
                                        String.format(
                                                        "Urządzenie %s pracuje poprawnie w operacyjnym zakresie temperatur (%.1f°C do %.1f°C) "
                                                                        + "oraz w zakresie temperatur dla materiału: %s. "
                                                                        + "Średnia temperatura urządzenia wynosi %.1f°C. Zgodność: %s.",
                                                        safe(device.getInventoryNumber()), minOp, maxOp,
                                                        safe(device.getMaterialName()),
                                                        avgTemp,
                                                        stats != null ? stats.getFormattedCompliancePercentage() + "%"
                                                                        : "–"),
                                        normal, bold);
                        addMetaRow(concl, "Wynik:", "POZYTYWNY", normal, bold);
                } else {
                        StringBuilder reason = new StringBuilder();
                        reason.append(String.format("Urządzenie %s — wykryto odchylenia. ",
                                        safe(device.getInventoryNumber())));
                        if (!deviceOk && avgTemp != null) {
                                reason.append(
                                                String.format("Średnia temp. urządzenia (%.1f°C) poza zakresem operacyjnym (%.1f–%.1f°C). ",
                                                                avgTemp, minOp != null ? minOp : 0,
                                                                maxOp != null ? maxOp : 0));
                        }
                        if (!complianceOk && stats != null && stats.getGlobalCompliancePercentage() != null) {
                                reason.append(String.format(
                                                "Zgodność temperaturowa: %.1f%% (poniżej wymaganego progu 95%%). ",
                                                stats.getGlobalCompliancePercentage()));
                        }
                        addMetaRow(concl, "Ocena:", reason.toString(), normal, bold);
                        addMetaRow(concl, "Wynik:", "NEGATYWNY — WYMAGANE DZIAŁANIA KORYGUJĄCE", normal, bold);
                }
                doc.add(concl);

                // Akceptacja
                doc.add(new Paragraph("").setMarginBottom(6));
                Table accept = new Table(UnitValue.createPercentArray(new float[] { 50, 25, 25 }))
                                .setWidth(UnitValue.createPercentValue(100)).setMarginBottom(14);
                accept.addCell(new Cell().add(new Paragraph("Akceptacja wyników:").setFont(bold).setFontSize(9))
                                .setBackgroundColor(CLR_META_LBL).setBorder(new SolidBorder(BORDER)).setPadding(6));
                accept.addCell(new Cell()
                                .add(new Paragraph(allOk ? "[X] TAK" : "[ ] TAK").setFont(normal).setFontSize(9)
                                                .setTextAlignment(TextAlignment.CENTER))
                                .setBorder(new SolidBorder(BORDER)).setPadding(6)
                                .setBackgroundColor(allOk ? new DeviceRgb(220, 252, 231) : null));
                accept.addCell(new Cell()
                                .add(new Paragraph(!allOk ? "[X] NIE" : "[ ] NIE").setFont(normal).setFontSize(9)
                                                .setTextAlignment(TextAlignment.CENTER))
                                .setBorder(new SolidBorder(BORDER)).setPadding(6)
                                .setBackgroundColor(!allOk ? new DeviceRgb(254, 226, 226) : null));
                doc.add(accept);

                // Data następnej walidacji
                String nextDate;
                if (!allOk) {
                        nextDate = "NIEZWŁOCZNIE PO PODJĘTYCH DZIAŁANIACH NAPRAWCZYCH";
                } else {
                        // Rok po zakończeniu walidacji
                        LocalDateTime end = stats != null && stats.getValidationEndTime() != null
                                        ? stats.getValidationEndTime()
                                        : validation.getCreatedDate();
                        nextDate = end.toLocalDate().plusYears(1).format(DATE_FMT);
                }

                Table nextVal = metaTable();
                addMetaRow(nextVal, "Data następnej walidacji:", nextDate, normal, bold);
                doc.add(nextVal);

                // Podpisy
                doc.add(new Paragraph("").setMarginTop(24));
                Table sigs = new Table(UnitValue.createPercentArray(new float[] { 50, 50 }))
                                .setWidth(UnitValue.createPercentValue(100)).setMarginTop(20);
                sigs.addCell(new Cell().add(new Paragraph("Sporządził:").setFont(bold).setFontSize(9))
                                .setBorder(Border.NO_BORDER).setPaddingBottom(30));
                sigs.addCell(new Cell().add(new Paragraph("Zatwierdził:").setFont(bold).setFontSize(9))
                                .setBorder(Border.NO_BORDER).setPaddingBottom(30));
                sigs.addCell(new Cell()
                                .add(new Paragraph("....................................").setFont(normal)
                                                .setFontSize(8)
                                                .setTextAlignment(TextAlignment.CENTER))
                                .setBorder(Border.NO_BORDER));
                sigs.addCell(new Cell()
                                .add(new Paragraph("....................................").setFont(normal)
                                                .setFontSize(8)
                                                .setTextAlignment(TextAlignment.CENTER))
                                .setBorder(Border.NO_BORDER));
                doc.add(sigs);
        }

        // =========================================================================
        // STOPKA
        // =========================================================================

        private void addFooter(Document doc, Validation validation, PdfFont normal) {
                String footer = String.format(
                                "RPW: %s   |   Walidacja ID: %d   |   Wygenerowano: %s   |   System Walidacji v2.11.0",
                                safe(validation.getValidationPlanNumber()),
                                validation.getId(),
                                LocalDateTime.now().format(DT_FMT));

                doc.add(new Paragraph(footer)
                                .setFont(normal).setFontSize(7)
                                .setFontColor(ColorConstants.GRAY)
                                .setTextAlignment(TextAlignment.CENTER)
                                .setMarginTop(14));
        }

        // =========================================================================
        // HELPERY
        // =========================================================================

        private void addSectionHeader(Document doc, String title, PdfFont bold) {
                doc.add(new Paragraph(title)
                                .setFont(bold).setFontSize(9)
                                .setFontColor(ColorConstants.WHITE)
                                .setBackgroundColor(CLR_SECTION)
                                .setPaddingTop(5).setPaddingBottom(5).setPaddingLeft(8)
                                .setMarginTop(10).setMarginBottom(6));
        }

        private void addTableHeader(Table table, String[] headers, PdfFont bold) {
                for (String hdr : headers) {
                        table.addHeaderCell(new Cell()
                                        .add(new Paragraph(hdr).setFont(bold).setFontSize(7.5f)
                                                        .setTextAlignment(TextAlignment.CENTER))
                                        .setBackgroundColor(CLR_HDR)
                                        .setBorder(new SolidBorder(BORDER))
                                        .setPadding(4));
                }
        }

        private void addStatRow(Table table, String nr, String param, String value, String unit, PdfFont normal) {
                table.addCell(dataCell(nr, normal, TextAlignment.CENTER));
                table.addCell(dataCell(param, normal, TextAlignment.LEFT));
                table.addCell(dataCell(safe(value), normal, TextAlignment.CENTER));
                table.addCell(dataCell(unit, normal, TextAlignment.CENTER));
        }

        private Table metaTable() {
                return new Table(UnitValue.createPercentArray(new float[] { 30, 70 }))
                                .setWidth(UnitValue.createPercentValue(100))
                                .setMarginBottom(6);
        }

        private void addMetaRow(Table table, String label, String value, PdfFont normal, PdfFont bold) {
                table.addCell(new Cell()
                                .add(new Paragraph(label).setFont(bold).setFontSize(8))
                                .setBackgroundColor(CLR_META_LBL)
                                .setBorder(new SolidBorder(BORDER))
                                .setPadding(4));
                table.addCell(new Cell()
                                .add(new Paragraph(safe(value)).setFont(normal).setFontSize(8))
                                .setBorder(new SolidBorder(BORDER))
                                .setPadding(4));
        }

        private Cell dataCell(String text, PdfFont font, TextAlignment align) {
                return new Cell()
                                .add(new Paragraph(safe(text)).setFont(font).setFontSize(7.5f)
                                                .setTextAlignment(align))
                                .setBorder(new SolidBorder(BORDER))
                                .setPadding(3);
        }

        private DeviceRgb getDeviationRowColor(DeviationSeverity severity) {
                switch (severity) {
                        case CRITICAL:
                                return CLR_DANGER;
                        case MAJOR:
                                return CLR_WARN;
                        case MINOR:
                                return CLR_PURPLE;
                        default:
                                return CLR_PURPLE;
                }
        }

        private String fmtTemp(Double temp) {
                return temp != null ? String.format("%.2f", temp) : "–";
        }

        private String safe(String s) {
                return s != null && !s.isBlank() ? s : "–";
        }

        private int nn(Integer i) {
                return i != null ? i : 0;
        }

        private String buildTempRange(CoolingDevice device) {
                String min = device.getMinOperatingTemp() != null
                                ? String.format("%.1f", device.getMinOperatingTemp())
                                : "–";
                String max = device.getMaxOperatingTemp() != null
                                ? String.format("%.1f", device.getMaxOperatingTemp())
                                : "–";
                return min + "°C do " + max + "°C";
        }

        /**
         * Zwraca informację o położeniu hotspot w siatce 3x3x3.
         */
        private String getHotspotLocation(Validation validation, ValidationSummaryStatsDto stats) {
                if (stats.getHotspotSeriesId() == null) {
                        return "Nie zidentyfikowano";
                }

                return validation.getMeasurementSeries().stream()
                        .filter(s -> s.getId().equals(stats.getHotspotSeriesId()))
                        .findFirst()
                        .map(s -> {
                                if (Boolean.TRUE.equals(s.getIsReferenceRecorder())) {
                                        return "Rejestrator referencyjny";
                                }
                                return s.getRecorderPosition() != null ?
                                        s.getRecorderPosition().getDisplayName() : "Nieznane położenie";
                        })
                        .orElse("Nie znaleziono serii");
        }

        /**
         * Zwraca informację o położeniu coldspot w siatce 3x3x3.
         */
        private String getColdspotLocation(Validation validation, ValidationSummaryStatsDto stats) {
                if (stats.getColdspotSeriesId() == null) {
                        return "Nie zidentyfikowano";
                }

                return validation.getMeasurementSeries().stream()
                        .filter(s -> s.getId().equals(stats.getColdspotSeriesId()))
                        .findFirst()
                        .map(s -> {
                                if (Boolean.TRUE.equals(s.getIsReferenceRecorder())) {
                                        return "Rejestrator referencyjny";
                                }
                                return s.getRecorderPosition() != null ?
                                        s.getRecorderPosition().getDisplayName() : "Nieznane położenie";
                        })
                        .orElse("Nie znaleziono serii");
        }

        /**
         * Zwraca informację o czujniku kontrolującym urządzenie.
         * Przyjmujemy, że czujnik kontrolujący to pierwszy niereferencyjny rejestrator
         * lub seria z pozycją MIDDLE_CENTER_CENTER (centrum urządzenia).
         */
        private String getControlSensorInfo(Validation validation) {
                // Najpierw szukaj centrum siatki (S5)
                var centerSeries = validation.getMeasurementSeries().stream()
                        .filter(s -> Boolean.FALSE.equals(s.getIsReferenceRecorder()))
                        .filter(s -> s.getRecorderPosition() != null)
                        .filter(s -> "MIDDLE_CENTER_CENTER".equals(s.getRecorderPosition().name()))
                        .findFirst();

                if (centerSeries.isPresent()) {
                        MeasurementSeries series = centerSeries.get();
                        return String.format("S/N %s (pozycja: %s - centrum urządzenia)",
                                safe(series.getRecorderSerialNumber()),
                                series.getRecorderPosition().getDisplayName());
                }

                // Jeśli brak centrum, weź pierwszy niereferencyjny rejestrator
                var firstGridSeries = validation.getMeasurementSeries().stream()
                        .filter(s -> Boolean.FALSE.equals(s.getIsReferenceRecorder()))
                        .findFirst();

                if (firstGridSeries.isPresent()) {
                        MeasurementSeries series = firstGridSeries.get();
                        String position = series.getRecorderPosition() != null ?
                                series.getRecorderPosition().getDisplayName() : "nieznana pozycja";
                        return String.format("S/N %s (pozycja: %s - domyślny kontrolny)",
                                safe(series.getRecorderSerialNumber()), position);
                }

                return "Brak rejestratorów siatki";
        }

        /**
         * Dodaje wiersz z informacją przestrzenną (inny styl niż standardowe statystyki).
         */
        private void addSpatialInfoRow(Table table, String code, String label, String value, String unit, PdfFont normal) {
                Cell codeCell = new Cell()
                        .add(new Paragraph(code).setFont(normal).setFontSize(8))
                        .setBorder(new SolidBorder(BORDER))
                        .setPadding(4)
                        .setBackgroundColor(new DeviceRgb(240, 248, 255)) // jasno-niebieski
                        .setTextAlignment(TextAlignment.CENTER);

                Cell labelCell = new Cell()
                        .add(new Paragraph(label).setFont(normal).setFontSize(8))
                        .setBorder(new SolidBorder(BORDER))
                        .setPadding(4)
                        .setBackgroundColor(new DeviceRgb(240, 248, 255));

                Cell valueCell = new Cell()
                        .add(new Paragraph(value + (unit.isEmpty() ? "" : " " + unit))
                                .setFont(normal).setFontSize(8))
                        .setBorder(new SolidBorder(BORDER))
                        .setPadding(4)
                        .setBackgroundColor(new DeviceRgb(240, 248, 255))
                        .setTextAlignment(TextAlignment.CENTER);

                Cell unitCell = new Cell()
                        .add(new Paragraph("").setFont(normal).setFontSize(8))
                        .setBorder(new SolidBorder(BORDER))
                        .setPadding(4)
                        .setBackgroundColor(new DeviceRgb(240, 248, 255));

                table.addCell(codeCell);
                table.addCell(labelCell);
                table.addCell(valueCell);
                table.addCell(unitCell);
        }

        /**
         * Dodaje sekcję z kryteriami akceptacji walidacji.
         * Zawiera jasne kryteria zgodności z GMP/GDP.
         */
        private void addAcceptanceCriteria(Document doc, Validation validation, CoolingDevice device,
                                         PdfFont normal, PdfFont bold) {
                addSectionHeader(doc, "KRYTERIA AKCEPTACJI WALIDACJI", bold);

                // Tabela z kryteriami (może się łamać między stronami - za duża)
                Table table = new Table(UnitValue.createPercentArray(new float[] { 5, 50, 25, 20 }))
                        .setWidth(UnitValue.createPercentValue(100))
                        .setMarginBottom(20)
                        .setFontSize(9);

                addTableHeader(table, new String[] { "Nr", "Kryterium", "Wymagana wartość", "Podstawa prawna" }, bold);

                // K1: Zakres temperatur
                String tempRange = buildTempRange(device);
                addCriteriumRow(table, "K1", "Temperatura w zakresie operacyjnym urządzenia", tempRange,
                               "Specyfikacja urządzenia", normal);

                // K2: Zgodność temperaturowa
                addCriteriumRow(table, "K2", "Globalny wskaźnik zgodności temperaturowej", "≥ 95%",
                               "GMP Annex 15 (EU)", normal);

                // K3: Czas pojedynczego naruszenia
                addCriteriumRow(table, "K3", "Maksymalny czas pojedynczego naruszenia", "≤ 30 minut",
                               "WHO TRS 961 (2011)", normal);

                // K4: Stabilność termiczna
                addCriteriumRow(table, "K4", "Współczynnik zmienności (CV)", "≤ 5%",
                               "ICH Q1A(R2)", normal);

                // K5: Mapowanie przestrzenne
                String gridCoverage = getGridCoverage(validation);
                addCriteriumRow(table, "K5", "Pokrycie siatki pomiarowej", gridCoverage,
                               "GDP Chapter 9 (EU)", normal);

                // K6: Kalibracja rejestratorów
                addCriteriumRow(table, "K6", "Ważność kalibracji rejestratorów", "Wszystkie ważne w dniu pomiaru",
                               "ISO 17025", normal);

                // K7: Dokumentacja odchyleń
                addCriteriumRow(table, "K7", "Analiza CAPA dla odchyleń krytycznych", "Wymagana dla każdego naruszenia",
                               "GMP Chapter 1", normal);

                doc.add(table);

                // Dodatkowe uwagi
                doc.add(new Paragraph("Uwagi:")
                        .setFont(bold)
                        .setFontSize(10)
                        .setMarginTop(10)
                        .setMarginBottom(5));

                doc.add(new Paragraph("• Walidacja zostanie uznana za zakończoną pomyślnie tylko w przypadku spełnienia WSZYSTKICH powyższych kryteriów.")
                        .setFont(normal)
                        .setFontSize(9)
                        .setMarginBottom(3));

                doc.add(new Paragraph("• Każde odchylenie od kryteriów musi być udokumentowane, przeanalizowane pod kątem wpływu na jakość i bezpieczeństwo produktu.")
                        .setFont(normal)
                        .setFontSize(9)
                        .setMarginBottom(3));

                doc.add(new Paragraph("• Podpisanie elektroniczne protokołu walidacji potwierdza spełnienie wszystkich kryteriów akceptacji.")
                        .setFont(normal)
                        .setFontSize(9)
                        .setMarginBottom(15));
        }

        /**
         * Dodaje wiersz kryterium do tabeli kryteriów akceptacji.
         */
        private void addCriteriumRow(Table table, String code, String criterion, String requiredValue,
                                   String legalBasis, PdfFont normal) {
                // Kod kryterium
                Cell codeCell = new Cell()
                        .add(new Paragraph(code).setFont(normal).setBold())
                        .setBorder(new SolidBorder(BORDER))
                        .setPadding(6)
                        .setBackgroundColor(new DeviceRgb(252, 248, 227)) // jasno-żółty
                        .setTextAlignment(TextAlignment.CENTER);

                // Opis kryterium
                Cell criterionCell = new Cell()
                        .add(new Paragraph(criterion).setFont(normal))
                        .setBorder(new SolidBorder(BORDER))
                        .setPadding(6);

                // Wymagana wartość
                Cell valueCell = new Cell()
                        .add(new Paragraph(requiredValue).setFont(normal).setBold())
                        .setBorder(new SolidBorder(BORDER))
                        .setPadding(6)
                        .setBackgroundColor(new DeviceRgb(240, 248, 255)) // jasno-niebieski
                        .setTextAlignment(TextAlignment.CENTER);

                // Podstawa prawna
                Cell basisCell = new Cell()
                        .add(new Paragraph(legalBasis).setFont(normal).setFontSize(8))
                        .setBorder(new SolidBorder(BORDER))
                        .setPadding(6)
                        .setTextAlignment(TextAlignment.CENTER);

                table.addCell(codeCell);
                table.addCell(criterionCell);
                table.addCell(valueCell);
                table.addCell(basisCell);
        }

        /**
         * Oblicza pokrycie siatki pomiarowej.
         */
        private String getGridCoverage(Validation validation) {
                long gridSeriesCount = validation.getMeasurementSeries().stream()
                        .filter(s -> Boolean.FALSE.equals(s.getIsReferenceRecorder()))
                        .count();

                int totalPossiblePositions = 27; // 3x3x3 grid
                double coveragePercent = (gridSeriesCount * 100.0) / totalPossiblePositions;

                return String.format("%d/%d pozycji (%.1f%%)", gridSeriesCount, totalPossiblePositions, coveragePercent);
        }

        /**
         * Tworzy komórkę nagłówka tabeli.
         */
        private Cell createHeaderCell(String text, PdfFont bold) {
                return new Cell()
                        .add(new Paragraph(text).setFont(bold).setFontSize(9))
                        .setTextAlignment(TextAlignment.CENTER)
                        .setBackgroundColor(CLR_HDR)
                        .setBorder(new SolidBorder(BORDER))
                        .setPadding(6);
        }

        /**
         * Tworzy komórkę danych tabeli.
         */
        private Cell createDataCell(String text, PdfFont font, TextAlignment alignment) {
                return new Cell()
                        .add(new Paragraph(text).setFont(font).setFontSize(9))
                        .setTextAlignment(alignment)
                        .setBorder(new SolidBorder(BORDER))
                        .setPadding(6);
        }

        /**
         * Event handler dla numeracji stron w stopce dokumentu.
         * POPRAWKA: Używa szablon XObject dla poprawnej numeracji.
         */
        private static class PageNumberEventHandler implements IEventHandler {
                private final PdfFont font;
                private final Map<Integer, PdfFormXObject> pageTemplates = new HashMap<>();

                public PageNumberEventHandler(PdfFont font) {
                        this.font = font;
                }

                @Override
                public void handleEvent(com.itextpdf.kernel.events.Event event) {
                        PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
                        PdfDocument pdfDoc = docEvent.getDocument();
                        PdfPage page = docEvent.getPage();
                        PdfCanvas canvas = new PdfCanvas(page);

                        int pageNumber = pdfDoc.getPageNumber(page);

                        // Pozycja w stopce (środek, 20 punktów od dołu)
                        float x = (page.getPageSize().getLeft() + page.getPageSize().getRight()) / 2;
                        float y = 20;

                        // Dodaj numer strony
                        canvas.beginText()
                              .setFontAndSize(font, 8)
                              .setColor(ColorConstants.GRAY, false)
                              .moveText(x - 30, y) // Przesunięcie w lewo dla miejsca na "z [total]"
                              .showText("Strona " + pageNumber + " z ")
                              .endText();

                        // Utwórz szablon XObject dla łącznej liczby stron
                        PdfFormXObject template = new PdfFormXObject(new Rectangle(0, 0, 20, 10));
                        pageTemplates.put(pageNumber, template);

                        // Dodaj szablon na pozycji po "z "
                        canvas.addXObjectAt(template, x + 10, y);
                }

                /**
                 * Wypełnia szablony rzeczywistą liczbą stron po zakończeniu dokumentu
                 */
                public void fillTemplates(PdfDocument pdfDoc) {
                        int totalPages = pdfDoc.getNumberOfPages();
                        String totalPagesText = String.valueOf(totalPages);

                        for (PdfFormXObject template : pageTemplates.values()) {
                                PdfCanvas templateCanvas = new PdfCanvas(template, pdfDoc);
                                templateCanvas.beginText()
                                            .setFontAndSize(font, 8)
                                            .setColor(ColorConstants.GRAY, false)
                                            .moveText(0, 0)
                                            .showText(totalPagesText)
                                            .endText();
                        }
                }
        }
}
