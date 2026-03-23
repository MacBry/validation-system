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
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Div;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.mac.bry.validationsystem.device.CoolingDevice;
import com.mac.bry.validationsystem.measurement.MeasurementSeries;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Serwis generujący graficzny schemat rozmieszczenia rejestratorów temperatury
 * w formacie PDF na podstawie wzorca Schemat_Wizualny_12x12.
 *
 * Siatka 3 poziomy × 3×3 pozycje = 27 rejestratorów.
 * Klucze pozycji (G1–G9, S1–S9, D1–D9) odpowiadają dokumentKey z RecorderPosition.
 */
@Slf4j
@Service
public class SchematWizualnyPdfService {

    // ── Kolory ───────────────────────────────────────────────────────────────
    private static final DeviceRgb COLOR_HEADER      = new DeviceRgb(31,  73, 125);   // ciemny granat
    private static final DeviceRgb COLOR_LEVEL       = new DeviceRgb(68, 114, 196);   // niebieski
    private static final DeviceRgb COLOR_ROW_HDR     = new DeviceRgb(189, 215, 238);  // jasny niebieski
    private static final DeviceRgb COLOR_META_LBL    = new DeviceRgb(242, 242, 242);  // jasnoszary
    private static final DeviceRgb COLOR_REF_HDR     = new DeviceRgb(180, 100,  20);  // brązowy (sekcja REF)
    private static final DeviceRgb COLOR_MINI_EMPTY  = new DeviceRgb(240, 240, 240);  // szary – wolna komórka mini-siatki
    private static final DeviceRgb COLOR_MINI_EMPTY_TXT = new DeviceRgb(160, 160, 160); // tekst wolnej komórki
    private static final DeviceRgb COLOR_MINI_OUTER  = new DeviceRgb(220, 220, 220);  // obramowanie zewnętrznej komórki

    private static final float BORDER      = 0.5f;
    private static final float MINI_BORDER = 0.4f;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    // ── Układ siatki ─────────────────────────────────────────────────────────
    private static final String[][] KEYS_TOP    = {{"G1","G2","G3"},{"G4","G5","G6"},{"G7","G8","G9"}};
    private static final String[][] KEYS_MIDDLE = {{"S1","S2","S3"},{"S4","S5","S6"},{"S7","S8","S9"}};
    private static final String[][] KEYS_BOTTOM = {{"D1","D2","D3"},{"D4","D5","D6"},{"D7","D8","D9"}};

    private static final String[] ROW_LABELS      = {"Tył", "Środek", "Przód"};
    private static final String[] ROW_LABELS_MINI = {"T",   "Ś",     "P"};
    private static final String[] COL_LABELS      = {"Lewy", "Środek", "Prawy"};
    private static final String[] COL_LABELS_MINI = {"L",    "Śr.",   "P"};

    // ── API publiczne ─────────────────────────────────────────────────────────

    /**
     * Generuje PDF bez metadanych dokumentu w stopce (kompatybilność wsteczna).
     */
    public byte[] generateSchematWizualnyPdf(Validation validation) throws IOException {
        return generateSchematWizualnyPdf(validation, null, null, null);
    }

    /**
     * Generuje PDF z numerem dokumentu i danymi autora w stopce.
     *
     * @param documentNumber numer z rejestru, np. SW/LZTHLA/2026/001 (null → pominięty)
     * @param generatedBy    nazwa użytkownika (null → "system")
     * @param generatedAt    czas generacji (null → bieżący czas)
     */
    public byte[] generateSchematWizualnyPdf(Validation validation,
                                              String documentNumber,
                                              String generatedBy,
                                              java.time.LocalDateTime generatedAt) throws IOException {
        log.info("Generowanie Schematu Wizualnego PDF dla walidacji ID: {}", validation.getId());

        CoolingDevice device = validation.getCoolingDevice();

        Map<String, MeasurementSeries> byKey = validation.getMeasurementSeries().stream()
                .filter(s -> s.getRecorderPosition() != null)
                .collect(Collectors.toMap(
                        s -> s.getRecorderPosition().getDocumentKey(),
                        s -> s,
                        (a, b) -> a));

        java.util.List<MeasurementSeries> refSeries = validation.getMeasurementSeries().stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsReferenceRecorder()))
                .collect(Collectors.toList());

        String controlKey = (validation.getControlSensorPosition() != null)
                ? validation.getControlSensorPosition().getDocumentKey()
                : null;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(baos));
             Document doc = new Document(pdfDoc, PageSize.A4)) {

            doc.setMargins(44, 44, 44, 44);

            PdfFont bold   = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD, PdfEncodings.CP1250, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
            PdfFont normal = PdfFontFactory.createFont(StandardFonts.HELVETICA,      PdfEncodings.CP1250, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);

            // 1. Tytuł
            addTitle(doc, bold);

            // 2. Metryka urządzenia
            addMetadataTable(doc, device, normal, bold);

            // 3. Trzy poziomy siatki (bez podświetlenia)
            addLevelSection(doc, "GÓRA / SUFIT",  KEYS_TOP,    byKey, normal, bold);
            addLevelSection(doc, "ŚRODEK",         KEYS_MIDDLE, byKey, normal, bold);
            addLevelSection(doc, "DÓŁ / PODŁOGA", KEYS_BOTTOM, byKey, normal, bold);

            // 4. Rejestrator referencyjny (jeśli istnieje)
            if (!refSeries.isEmpty()) {
                addReferenceSection(doc, refSeries, normal, bold);
            }

            // 5. Czujnik kontrolujący – kompaktowa siatka 3 poziomów
            if (controlKey != null) {
                addControlSensorSection(doc, controlKey, normal, bold);
            }

            // 6. Stopka
            java.time.LocalDateTime ts = generatedAt != null ? generatedAt : java.time.LocalDateTime.now();
            String author = generatedBy != null ? generatedBy : "system";
            addFooter(doc, validation, documentNumber, author, ts, normal, bold);
        }

        log.info("Schemat Wizualny PDF gotowy: {} bajtów", baos.size());
        return baos.toByteArray();
    }

    // ── Sekcje dokumentu ─────────────────────────────────────────────────────

    private void addTitle(Document doc, PdfFont bold) {
        doc.add(new Paragraph("GRAFICZNY SCHEMAT ROZMIESZCZENIA REJESTRATORÓW TEMPERATURY")
                .setFont(bold)
                .setFontSize(10)
                .setFontColor(ColorConstants.WHITE)
                .setTextAlignment(TextAlignment.CENTER)
                .setBackgroundColor(COLOR_HEADER)
                .setPaddingTop(7).setPaddingBottom(7)
                .setMarginBottom(6));
    }

    private void addMetadataTable(Document doc, CoolingDevice device, PdfFont normal, PdfFont bold) {
        String lab  = device.getLaboratory() != null ? device.getLaboratory().getFullName() : "–";
        String dept = device.getDepartment() != null ? device.getDepartment().getName() : "–";
        String loc  = device.getLaboratory() != null ? lab + " / " + dept : dept;

        Table table = new Table(UnitValue.createPercentArray(new float[]{28, 72}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(6);

        addMetaRow(table, "Nazwa urządzenia:",  safe(device.getName()),            normal, bold);
        addMetaRow(table, "Lokalizacja:",        loc,                               normal, bold);
        addMetaRow(table, "Nr inwentarzowy:",    safe(device.getInventoryNumber()), normal, bold);
        addMetaRow(table, "Materiał:",           safe(device.getMaterialName()),    normal, bold);
        addMetaRow(table, "Zakres temp. [°C]:", buildTempRange(device),            normal, bold);

        doc.add(table);
    }

    private void addLevelSection(Document doc, String levelName, String[][] keys,
                                 Map<String, MeasurementSeries> byKey,
                                 PdfFont normal, PdfFont bold) {
        Div section = new Div().setKeepTogether(true).setMarginTop(5).setMarginBottom(4);

        section.add(new Paragraph("POZIOM: " + levelName)
                .setFont(bold).setFontSize(8.5f)
                .setFontColor(ColorConstants.WHITE)
                .setBackgroundColor(COLOR_LEVEL)
                .setPaddingTop(4).setPaddingBottom(4).setPaddingLeft(6)
                .setMarginBottom(3));

        float[] colWidths = {10f, 30f, 30f, 30f};
        Table grid = new Table(UnitValue.createPercentArray(colWidths))
                .setWidth(UnitValue.createPercentValue(100));

        grid.addHeaderCell(emptyCell(bold));
        for (String label : COL_LABELS) {
            grid.addHeaderCell(new Cell()
                    .add(new Paragraph(label).setFont(bold).setFontSize(8)
                            .setTextAlignment(TextAlignment.CENTER))
                    .setBackgroundColor(COLOR_ROW_HDR)
                    .setBorder(new SolidBorder(BORDER))
                    .setPadding(3));
        }

        for (int row = 0; row < 3; row++) {
            grid.addCell(new Cell()
                    .add(new Paragraph(ROW_LABELS[row]).setFont(bold).setFontSize(8)
                            .setTextAlignment(TextAlignment.CENTER))
                    .setBackgroundColor(COLOR_ROW_HDR)
                    .setBorder(new SolidBorder(BORDER))
                    .setPadding(3));

            for (int col = 0; col < 3; col++) {
                String key = keys[row][col];
                grid.addCell(buildPositionCell(key, byKey.get(key), normal, bold));
            }
        }

        section.add(grid);
        doc.add(section);
    }

    private void addReferenceSection(Document doc, java.util.List<MeasurementSeries> refSeries,
                                     PdfFont normal, PdfFont bold) {
        Div section = new Div().setKeepTogether(true).setMarginTop(5).setMarginBottom(4);

        section.add(new Paragraph("REJESTRATOR REFERENCYJNY (zewnętrzny)")
                .setFont(bold).setFontSize(8.5f)
                .setFontColor(ColorConstants.WHITE)
                .setBackgroundColor(COLOR_REF_HDR)
                .setPaddingTop(4).setPaddingBottom(4).setPaddingLeft(6)
                .setMarginBottom(3));

        float[] colWidths = {25f, 25f, 25f, 25f};
        Table table = new Table(UnitValue.createPercentArray(colWidths))
                .setWidth(UnitValue.createPercentValue(100));

        for (String hdr : new String[]{"S/N rejestratora", "T.Max", "T.Min", "T.Śr."}) {
            table.addHeaderCell(new Cell()
                    .add(new Paragraph(hdr).setFont(bold).setFontSize(8)
                            .setTextAlignment(TextAlignment.CENTER))
                    .setBackgroundColor(COLOR_ROW_HDR)
                    .setBorder(new SolidBorder(BORDER))
                    .setPadding(3));
        }

        for (MeasurementSeries s : refSeries) {
            table.addCell(new Cell().add(new Paragraph(safe(s.getRecorderSerialNumber()))
                    .setFont(normal).setFontSize(8)).setBorder(new SolidBorder(BORDER)).setPadding(3));
            table.addCell(new Cell().add(new Paragraph(formatTemp(s.getMaxTemperature()))
                    .setFont(normal).setFontSize(8).setTextAlignment(TextAlignment.CENTER))
                    .setBorder(new SolidBorder(BORDER)).setPadding(3));
            table.addCell(new Cell().add(new Paragraph(formatTemp(s.getMinTemperature()))
                    .setFont(normal).setFontSize(8).setTextAlignment(TextAlignment.CENTER))
                    .setBorder(new SolidBorder(BORDER)).setPadding(3));
            table.addCell(new Cell().add(new Paragraph(formatTemp(s.getAvgTemperature()))
                    .setFont(normal).setFontSize(8).setTextAlignment(TextAlignment.CENTER))
                    .setBorder(new SolidBorder(BORDER)).setPadding(3));
        }

        section.add(table);
        doc.add(section);
    }

    /**
     * Sekcja czujnika kontrolującego – trzy kompaktowe siatki 3×3 obok siebie.
     * Układ identyczny z widokiem HTML (GÓRA | ŚRODEK | DÓŁ).
     */
    private void addControlSensorSection(Document doc, String controlKey,
                                         PdfFont normal, PdfFont bold) {
        Div section = new Div().setKeepTogether(true).setMarginTop(6).setMarginBottom(4);

        section.add(new Paragraph("CZUJNIK KONTROLUJĄCY URZĄDZENIE")
                .setFont(bold).setFontSize(8.5f)
                .setFontColor(ColorConstants.WHITE)
                .setBackgroundColor(COLOR_HEADER)
                .setPaddingTop(4).setPaddingBottom(4).setPaddingLeft(6)
                .setMarginBottom(4));

        // Trzy kolumny – jedna per poziom
        Table outer = new Table(UnitValue.createPercentArray(new float[]{33.33f, 33.33f, 33.33f}))
                .setWidth(UnitValue.createPercentValue(100));

        outer.addCell(buildMiniLevelCell("GÓRA / SUFIT",  KEYS_TOP,    controlKey, normal, bold));
        outer.addCell(buildMiniLevelCell("ŚRODEK",         KEYS_MIDDLE, controlKey, normal, bold));
        outer.addCell(buildMiniLevelCell("DÓŁ / PODŁOGA", KEYS_BOTTOM, controlKey, normal, bold));

        section.add(outer);
        doc.add(section);
    }

    /**
     * Pojedyncza kolumna mini-siatki (jeden poziom) dla sekcji czujnika kontrolującego.
     */
    private Cell buildMiniLevelCell(String levelName, String[][] keys,
                                    String controlKey,
                                    PdfFont normal, PdfFont bold) {
        Cell outer = new Cell()
                .setBorder(new SolidBorder(COLOR_MINI_OUTER, 0.5f))
                .setPadding(4);

        // Nagłówek poziomu
        outer.add(new Paragraph(levelName)
                .setFont(bold).setFontSize(7f)
                .setFontColor(ColorConstants.WHITE)
                .setBackgroundColor(COLOR_LEVEL)
                .setTextAlignment(TextAlignment.CENTER)
                .setPaddingTop(2).setPaddingBottom(2)
                .setMarginBottom(3));

        // Siatka 4 kolumny: [etykieta wiersza] + [L] + [Śr.] + [P]
        float[] colW = {12f, 29.33f, 29.33f, 29.33f};
        Table grid = new Table(UnitValue.createPercentArray(colW))
                .setWidth(UnitValue.createPercentValue(100));

        // Nagłówek kolumn
        grid.addHeaderCell(miniHdrCell("", bold));
        for (String lbl : COL_LABELS_MINI) {
            grid.addHeaderCell(miniHdrCell(lbl, bold));
        }

        // Wiersze danych
        for (int row = 0; row < 3; row++) {
            // Etykieta wiersza
            grid.addCell(new Cell()
                    .add(new Paragraph(ROW_LABELS_MINI[row])
                            .setFont(bold).setFontSize(6f)
                            .setTextAlignment(TextAlignment.CENTER)
                            .setFontColor(ColorConstants.GRAY))
                    .setBackgroundColor(COLOR_ROW_HDR)
                    .setBorder(new SolidBorder(MINI_BORDER))
                    .setPadding(2));

            for (int col = 0; col < 3; col++) {
                String key = keys[row][col];
                boolean selected = key.equals(controlKey);
                grid.addCell(buildMiniCell(key, selected, bold));
            }
        }

        outer.add(grid);
        return outer;
    }

    private Cell miniHdrCell(String text, PdfFont bold) {
        return new Cell()
                .add(new Paragraph(text).setFont(bold).setFontSize(6f)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setFontColor(ColorConstants.GRAY))
                .setBackgroundColor(COLOR_ROW_HDR)
                .setBorder(new SolidBorder(MINI_BORDER))
                .setPadding(2);
    }

    private Cell buildMiniCell(String key, boolean selected, PdfFont bold) {
        Cell cell = new Cell()
                .setBorder(new SolidBorder(MINI_BORDER))
                .setPadding(2)
                .setMinHeight(16f);

        if (selected) {
            cell.setBackgroundColor(COLOR_LEVEL);
            cell.add(new Paragraph(key)
                    .setFont(bold).setFontSize(7f)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(ColorConstants.WHITE));
        } else {
            cell.setBackgroundColor(COLOR_MINI_EMPTY);
            cell.add(new Paragraph(key)
                    .setFont(bold).setFontSize(6.5f)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(COLOR_MINI_EMPTY_TXT));
        }

        return cell;
    }

    private void addFooter(Document doc, Validation validation,
                           String documentNumber, String generatedBy,
                           java.time.LocalDateTime generatedAt,
                           PdfFont normal, PdfFont bold) {

        java.time.format.DateTimeFormatter dtFmt =
                java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

        String dateStr = generatedAt != null ? generatedAt.format(dtFmt) : "–";

        StringBuilder sb = new StringBuilder();
        if (documentNumber != null) {
            sb.append("Nr dok.: ").append(documentNumber).append("   |   ");
        }
        sb.append("RPW: ").append(safe(validation.getValidationPlanNumber()))
          .append("   |   Wygenerowano: ").append(dateStr)
          .append("   |   Przez: ").append(generatedBy != null ? generatedBy : "system");

        doc.add(new Paragraph(sb.toString())
                .setFont(normal).setFontSize(7)
                .setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(8));
    }

    // ── Komórki głównej siatki ────────────────────────────────────────────────

    private Cell buildPositionCell(String key, MeasurementSeries series,
                                   PdfFont normal, PdfFont bold) {
        Cell cell = new Cell()
                .setBorder(new SolidBorder(BORDER))
                .setPadding(4)
                .setMinHeight(52f);

        cell.add(new Paragraph(key)
                .setFont(bold).setFontSize(8)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(ColorConstants.WHITE)
                .setBackgroundColor(COLOR_LEVEL)
                .setMarginBottom(2));

        if (series != null) {
            cell.add(row(normal, "S/N: ",   safe(series.getRecorderSerialNumber())));
            cell.add(row(normal, "T.Max: ", formatTemp(series.getMaxTemperature())));
            cell.add(row(normal, "T.Min: ", formatTemp(series.getMinTemperature())));
            cell.add(row(normal, "T.Śr:  ", formatTemp(series.getAvgTemperature())));
        } else {
            cell.add(new Paragraph("brak danych")
                    .setFont(normal).setFontSize(7)
                    .setFontColor(ColorConstants.GRAY)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setItalic());
        }

        return cell;
    }

    private Paragraph row(PdfFont normal, String label, String value) {
        return new Paragraph(label + value)
                .setFont(normal).setFontSize(7)
                .setMarginBottom(1);
    }

    private void addMetaRow(Table table, String label, String value, PdfFont normal, PdfFont bold) {
        table.addCell(new Cell()
                .add(new Paragraph(label).setFont(bold).setFontSize(8))
                .setBackgroundColor(COLOR_META_LBL)
                .setBorder(new SolidBorder(BORDER))
                .setPadding(4));
        table.addCell(new Cell()
                .add(new Paragraph(value).setFont(normal).setFontSize(8))
                .setBorder(new SolidBorder(BORDER))
                .setPadding(4));
    }

    private Cell emptyCell(PdfFont bold) {
        return new Cell()
                .add(new Paragraph("").setFont(bold).setFontSize(8))
                .setBackgroundColor(COLOR_ROW_HDR)
                .setBorder(new SolidBorder(BORDER))
                .setPadding(3);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String formatTemp(Double temp) {
        return temp != null ? String.format("%.1f °C", temp) : "–";
    }

    private String safe(String s) {
        return s != null && !s.isBlank() ? s : "–";
    }

    private String buildTempRange(CoolingDevice device) {
        String min = device.getMinOperatingTemp() != null
                ? String.format("%.1f", device.getMinOperatingTemp()) : "–";
        String max = device.getMaxOperatingTemp() != null
                ? String.format("%.1f", device.getMaxOperatingTemp()) : "–";
        return min + " do " + max;
    }
}
