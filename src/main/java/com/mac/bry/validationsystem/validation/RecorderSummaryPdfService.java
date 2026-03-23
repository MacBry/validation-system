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
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.mac.bry.validationsystem.calibration.Calibration;
import com.mac.bry.validationsystem.device.CoolingDevice;
import com.mac.bry.validationsystem.measurement.MeasurementSeries;
import com.mac.bry.validationsystem.measurement.RecorderPosition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Serwis generujący raport PDF z zestawieniem użytych rejestratorów temperatury.
 *
 * <p>
 * Zawiera tabelę z danymi wszystkich rejestratorów użytych w walidacji:
 * nr seryjny, data wzorcowania, numer protokołu wzorcowania, ważność,
 * położenie na siatce 3x3x3, temperatury (min/max/śr/MKT).
 * </p>
 */
@Slf4j
@Service
public class RecorderSummaryPdfService {

    private static final DeviceRgb CLR_HDR = new DeviceRgb(240, 248, 255);
    private static final DeviceRgb CLR_GRID = new DeviceRgb(248, 250, 252);
    private static final DeviceRgb CLR_REF = new DeviceRgb(255, 248, 220);
    private static final float BORDER = 0.5f;

    public byte[] generateRecorderSummaryPdf(Validation validation) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document doc = new Document(pdf, PageSize.A4.rotate()); // Landscape dla szerokiej tabeli

        try {
            PdfFont normal = PdfFontFactory.createFont(StandardFonts.HELVETICA, PdfEncodings.CP1250);
            PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD, PdfEncodings.CP1250);

            addHeader(doc, validation, bold, normal);
            addRecorderTable(doc, validation, bold, normal);
            addFooter(doc, normal);

        } catch (Exception e) {
            log.error("Błąd generowania raportu rejestratorów PDF dla walidacji ID: {}", validation.getId(), e);
            throw new IOException("Błąd generowania raportu: " + e.getMessage(), e);
        }

        doc.close();
        log.info("Wygenerowano raport rejestratorów PDF dla walidacji ID: {} (rozmiar: {} KB)",
                validation.getId(), baos.size() / 1024);
        return baos.toByteArray();
    }

    private void addHeader(Document doc, Validation validation, PdfFont bold, PdfFont normal) {
        CoolingDevice device = validation.getCoolingDevice();
        String deviceInfo = device != null ?
            device.getInventoryNumber() + " – " + device.getName() : "Nieznane urządzenie";

        doc.add(new Paragraph("ZESTAWIENIE REJESTRATORÓW TEMPERATURY")
                .setFont(bold)
                .setFontSize(16)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(8));

        doc.add(new Paragraph("Urządzenie: " + deviceInfo)
                .setFont(normal)
                .setFontSize(10)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(4));

        doc.add(new Paragraph("Walidacja ID: " + validation.getId() +
                " • Data: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")))
                .setFont(normal)
                .setFontSize(9)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(20));
    }

    private void addRecorderTable(Document doc, Validation validation, PdfFont bold, PdfFont normal) {
        // Szerokość kolumn dla landscape A4
        float[] widths = {4, 12, 10, 15, 10, 18, 8, 8, 8, 7};
        Table table = new Table(UnitValue.createPercentArray(widths))
                .setWidth(UnitValue.createPercentValue(100))
                .setFontSize(8)
                .setMarginBottom(20);

        // Nagłówki tabeli
        String[] headers = {
            "LP", "Nr seryjny", "Data wzorcowania", "Nr protokołu wzorcowania",
            "Ważność do", "Położenie w siatce", "T.Min [°C]", "T.Max [°C]", "T.Śr [°C]", "MKT [°C]"
        };

        for (String header : headers) {
            table.addHeaderCell(createHeaderCell(header, bold));
        }

        // Podział na serie siatki i referencyjne
        List<MeasurementSeries> gridSeries = validation.getMeasurementSeries().stream()
                .filter(s -> Boolean.FALSE.equals(s.getIsReferenceRecorder()))
                .toList();

        List<MeasurementSeries> refSeries = validation.getMeasurementSeries().stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsReferenceRecorder()))
                .toList();

        int lp = 1;

        // Serie siatki (urządzenie)
        for (MeasurementSeries series : gridSeries) {
            addRecorderRow(table, lp++, series, normal, CLR_GRID, false);
        }

        // Serie referencyjne (otoczenie)
        for (MeasurementSeries series : refSeries) {
            addRecorderRow(table, lp++, series, normal, CLR_REF, true);
        }

        doc.add(table);

        // Legenda
        addLegend(doc, normal);
    }

    private void addRecorderRow(Table table, int lp, MeasurementSeries series,
                               PdfFont normal, DeviceRgb bgColor, boolean isReference) {

        // Dane kalibracji z najnowszego certyfikatu
        Calibration latestCal = series.getThermoRecorder() != null ?
            series.getThermoRecorder().getLatestCalibration() : null;

        String serialNumber = safe(series.getRecorderSerialNumber());
        String calDate = latestCal != null ?
            latestCal.getCalibrationDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) : "–";
        String certNumber = latestCal != null ? safe(latestCal.getCertificateNumber()) : "–";
        String validUntil = latestCal != null ?
            latestCal.getValidUntil().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) : "–";

        // Położenie w siatce
        String position;
        if (isReference) {
            position = "REFERENCYJNY (otoczenie)";
        } else {
            RecorderPosition pos = series.getRecorderPosition();
            position = pos != null ? pos.getDisplayName() : "–";
        }

        // Temperatury
        String minTemp = fmtTemp(series.getMinTemperature());
        String maxTemp = fmtTemp(series.getMaxTemperature());
        String avgTemp = fmtTemp(series.getAvgTemperature());
        String mktTemp = fmtTemp(series.getMktTemperature());

        Cell[] cells = {
            createDataCell(String.valueOf(lp), normal, TextAlignment.CENTER),
            createDataCell(serialNumber, normal, TextAlignment.LEFT),
            createDataCell(calDate, normal, TextAlignment.CENTER),
            createDataCell(certNumber, normal, TextAlignment.LEFT),
            createDataCell(validUntil, normal, TextAlignment.CENTER),
            createDataCell(position, normal, TextAlignment.LEFT),
            createDataCell(minTemp, normal, TextAlignment.CENTER),
            createDataCell(maxTemp, normal, TextAlignment.CENTER),
            createDataCell(avgTemp, normal, TextAlignment.CENTER),
            createDataCell(mktTemp, normal, TextAlignment.CENTER)
        };

        // Ustaw tło według typu rejestratora
        for (Cell cell : cells) {
            cell.setBackgroundColor(bgColor);
        }

        for (Cell cell : cells) {
            table.addCell(cell);
        }
    }

    private void addLegend(Document doc, PdfFont normal) {
        doc.add(new Paragraph("Legenda:")
                .setFont(normal)
                .setFontSize(9)
                .setBold()
                .setMarginTop(10)
                .setMarginBottom(5));

        Table legend = new Table(UnitValue.createPercentArray(new float[]{15, 85}))
                .setWidth(UnitValue.createPercentValue(50))
                .setFontSize(8);

        legend.addCell(createDataCell(" ", normal, TextAlignment.CENTER).setBackgroundColor(CLR_GRID));
        legend.addCell(createDataCell("Rejestratory siatki (wewnątrz urządzenia)", normal, TextAlignment.LEFT));

        legend.addCell(createDataCell(" ", normal, TextAlignment.CENTER).setBackgroundColor(CLR_REF));
        legend.addCell(createDataCell("Rejestratory referencyjne (temperatura otoczenia)", normal, TextAlignment.LEFT));

        doc.add(legend);
    }

    private void addFooter(Document doc, PdfFont normal) {
        doc.add(new Paragraph("Raport wygenerowany: " +
                LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")))
                .setFont(normal)
                .setFontSize(8)
                .setTextAlignment(TextAlignment.RIGHT)
                .setMarginTop(20));
    }

    private Cell createHeaderCell(String text, PdfFont bold) {
        return new Cell()
                .add(new Paragraph(text).setFont(bold))
                .setTextAlignment(TextAlignment.CENTER)
                .setBackgroundColor(CLR_HDR)
                .setBorder(new SolidBorder(BORDER))
                .setPadding(4);
    }

    private Cell createDataCell(String text, PdfFont font, TextAlignment alignment) {
        return new Cell()
                .add(new Paragraph(text).setFont(font))
                .setTextAlignment(alignment)
                .setBorder(new SolidBorder(BORDER))
                .setPadding(3);
    }

    private String safe(String str) {
        return str != null ? str : "–";
    }

    private String fmtTemp(Double temp) {
        return temp != null ? String.format("%.2f", temp) : "–";
    }
}