package com.mac.bry.validationsystem.validation;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.svg.converter.SvgConverter;
import com.mac.bry.validationsystem.measurement.MeasurementPoint;
import com.mac.bry.validationsystem.measurement.MeasurementPointRepository;
import com.mac.bry.validationsystem.measurement.MeasurementSeries;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.SVGGraphics2D;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.data.time.Minute;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.springframework.stereotype.Service;
import org.w3c.dom.DOMImplementation;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

/**
 * Serwis do generowania wykresów PDF
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChartPdfService {
    
    private final MeasurementPointRepository measurementPointRepository;
    
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final int CHART_WIDTH = 1200;
    private static final int CHART_HEIGHT = 500;
    
    /**
     * Generuje PDF z wykresem i tabelą danych dla serii pomiarowej
     */
    public byte[] generateChartPdf(MeasurementSeries series) throws IOException {
        log.debug("Generowanie PDF z wykresem dla serii ID: {}", series.getId());
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        
        // Dodaj event handler dla numeracji stron
        PdfFont pageFont = PdfFontFactory.createFont(StandardFonts.HELVETICA, "Cp1250", PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
        PageNumberEventHandler pageHandler = new PageNumberEventHandler(pageFont);
        pdfDoc.addEventHandler(com.itextpdf.kernel.events.PdfDocumentEvent.END_PAGE, pageHandler);
        
        Document document = new Document(pdfDoc, PageSize.A4.rotate()); // Landscape
        
        // Czcionka z obsługą polskich znaków
        PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA, "Cp1250", PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
        PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD, "Cp1250", PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
        
        // Nagłówek - tytuł
        String deviceName = series.getCoolingDevice() != null ? series.getCoolingDevice().getName() : "–";
        String inventoryNumber = series.getCoolingDevice() != null ? series.getCoolingDevice().getInventoryNumber() : "–";
        
        Paragraph title = new Paragraph("Wykres Temperatury - " + deviceName + " numer inwentarzowy: " + inventoryNumber)
            .setFont(boldFont)
            .setFontSize(14)
            .setTextAlignment(TextAlignment.CENTER)
            .setMarginBottom(10);
        document.add(title);
        
        // Informacje o serii - 3 kolumny w 4 wierszach (landscape)
        Table infoTable = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1}))
            .setWidth(UnitValue.createPercentValue(100))
            .setMarginBottom(15)
            .setFontSize(9);
        
        infoTable.addCell(createInfoCell("Nr serii pomiarowej:", String.valueOf(series.getId()), font, boldFont));
        infoTable.addCell(createInfoCell("Rejestrator:", series.getRecorderSerialNumber(), font, boldFont));
        infoTable.addCell(createInfoCell("Liczba pomiarów:", String.valueOf(series.getMeasurementCount()), font, boldFont));
        
        infoTable.addCell(createInfoCell("Pierwszy pomiar:", 
            series.getFirstMeasurementTime().format(DATE_TIME_FORMATTER), font, boldFont));
        infoTable.addCell(createInfoCell("Ostatni pomiar:", 
            series.getLastMeasurementTime().format(DATE_TIME_FORMATTER), font, boldFont));
        infoTable.addCell(createInfoCell("Pozycja:", 
            series.getRecorderPosition() != null ? series.getRecorderPosition().getDisplayName() : "–", 
            font, boldFont));
        
        infoTable.addCell(createInfoCell("Temp. min:", String.format("%.1f°C", series.getMinTemperature()), font, boldFont));
        infoTable.addCell(createInfoCell("Temp. śr.:", String.format("%.1f°C", series.getAvgTemperature()), font, boldFont));
        infoTable.addCell(createInfoCell("Temp. max:", String.format("%.1f°C", series.getMaxTemperature()), font, boldFont));
        
        infoTable.addCell(createInfoCell("Plik:", series.getOriginalFilename(), font, boldFont));
        
        document.add(infoTable);
        
        // Generuj wykres jako PNG
        byte[] chartPng = generateChartPng(series);
        
        // Konwertuj PNG do obrazka PDF
        com.itextpdf.io.image.ImageData imageData = 
            com.itextpdf.io.image.ImageDataFactory.create(chartPng);
        Image chartImage = new Image(imageData);
        
        // Użyj większości dostępnej szerokości strony (landscape A4 = ~840 punktów)
        float pageWidth = pdfDoc.getDefaultPageSize().getWidth();
        float margins = 80; // 40 z każdej strony
        chartImage.setWidth(pageWidth - margins);
        chartImage.setAutoScale(true);
        chartImage.setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.CENTER);
        chartImage.setMarginBottom(20);
        document.add(chartImage);
        
        // NOWA STRONA - Tabela z danymi pomiarowymi (LANDSCAPE)
        document.add(new com.itextpdf.layout.element.AreaBreak(PageSize.A4.rotate()));
        
        // Nagłówek drugiej strony
        Paragraph dataTitle = new Paragraph("Dane Pomiarowe - " + deviceName + " numer inwentarzowy: " + inventoryNumber)
            .setFont(boldFont)
            .setFontSize(14)
            .setTextAlignment(TextAlignment.CENTER)
            .setMarginBottom(10);
        document.add(dataTitle);
        
        // Informacje o serii - 3 kolumny w 3 wierszach (węższe dla portrait)
        Table dataInfoTable = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1}))
            .setWidth(UnitValue.createPercentValue(100))
            .setMarginBottom(15)
            .setFontSize(8);
        
        dataInfoTable.addCell(createInfoCell("Nr serii:", String.valueOf(series.getId()), font, boldFont));
        dataInfoTable.addCell(createInfoCell("Rejestrator:", series.getRecorderSerialNumber(), font, boldFont));
        dataInfoTable.addCell(createInfoCell("Pomiary:", String.valueOf(series.getMeasurementCount()), font, boldFont));
        
        dataInfoTable.addCell(createInfoCell("Pozycja:", 
            series.getRecorderPosition() != null ? series.getRecorderPosition().getDisplayName() : "–", 
            font, boldFont));
        dataInfoTable.addCell(createInfoCell("Od:", 
            series.getFirstMeasurementTime().format(DATE_TIME_FORMATTER), font, boldFont));
        dataInfoTable.addCell(createInfoCell("Do:", 
            series.getLastMeasurementTime().format(DATE_TIME_FORMATTER), font, boldFont));
        
        dataInfoTable.addCell(createInfoCell("Min:", String.format("%.1f°C", series.getMinTemperature()), font, boldFont));
        dataInfoTable.addCell(createInfoCell("Śr.:", String.format("%.1f°C", series.getAvgTemperature()), font, boldFont));
        dataInfoTable.addCell(createInfoCell("Max:", String.format("%.1f°C", series.getMaxTemperature()), font, boldFont));
        
        document.add(dataInfoTable);
        
        List<MeasurementPoint> points = measurementPointRepository.findBySeriesOrderByMeasurementTimeAsc(series);
        Table dataTable = createDataTable(points, font, boldFont);
        document.add(dataTable);
        
        document.close();
        
        log.debug("PDF wygenerowany: {} bajtów", baos.size());
        return baos.toByteArray();
    }
    
    private static class PageNumberEventHandler implements IEventHandler {
        private final PdfFont font;
        private final PdfFormXObject placeholder;

        public PageNumberEventHandler(PdfFont font) {
            this.font = font;
            this.placeholder = new PdfFormXObject(new Rectangle(0, 0, 30, 10));
        }

        @Override
        public void handleEvent(Event event) {
            PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
            PdfDocument pdfDoc = docEvent.getDocument();
            PdfPage page = docEvent.getPage();
            PdfCanvas canvas = new PdfCanvas(page);

            int pageNumber = pdfDoc.getPageNumber(page);

            // Pozycja w stopce dla strony Landscape (wyśrodkowanie)
            float x = (page.getPageSize().getLeft() + page.getPageSize().getRight()) / 2;
            float y = 20;

            String prefix = "Strona " + pageNumber + " z ";
            float prefixWidth = font.getWidth(prefix, 8);
            float startX = x - 45; // Przesunięcie nieco bardziej w lewo dla lepszego wyśrodkowania całości

            canvas.beginText()
                  .setFontAndSize(font, 8)
                  .setColor(ColorConstants.GRAY, false)
                  .moveText(startX, y)
                  .showText(prefix)
                  .endText();

            canvas.addXObjectAt(placeholder, startX + prefixWidth, y);
            
            // Aktualizuj całkowitą liczbę stron w szablonie (na bieżąco dla każdej strony)
            // Dzięki temu, że używamy jednego obiektu placeholder, wszystkie strony (nawet już przetworzone)
            // będą pokazywać ostateczną, poprawną wartość po zakończeniu generowania.
            writeTotalPages(pdfDoc);
            
            canvas.release();
        }

        private void writeTotalPages(PdfDocument pdfDoc) {
            int totalPages = pdfDoc.getNumberOfPages();
            String totalPagesText = String.valueOf(totalPages);

            // WYCZYŚĆ poprzednie dane w strumieniu placeholder-a, aby uniknąć nakładania się tekstu
            placeholder.getPdfObject().setData(new byte[0]);

            PdfCanvas canvas = new PdfCanvas(placeholder, pdfDoc);
            canvas.beginText()
                  .setFontAndSize(font, 8)
                  .setColor(ColorConstants.GRAY, false)
                  .moveText(0, 0)
                  .showText(totalPagesText)
                  .endText();
            canvas.release();
        }
    }
    
    /**
     * Generuje wykres jako PNG
     */
    private byte[] generateChartPng(MeasurementSeries series) throws IOException {
        // Pobierz dane
        List<MeasurementPoint> points = measurementPointRepository.findBySeriesOrderByMeasurementTimeAsc(series);
        
        // Utwórz dataset
        TimeSeries timeSeries = new TimeSeries("Temperatura");
        for (MeasurementPoint point : points) {
            Date date = Date.from(point.getMeasurementTime().atZone(ZoneId.systemDefault()).toInstant());
            timeSeries.add(new Minute(date), point.getTemperature());
        }
        
        TimeSeriesCollection dataset = new TimeSeriesCollection(timeSeries);
        
        // Utwórz wykres
        JFreeChart chart = ChartFactory.createTimeSeriesChart(
            null, // title
            "Czas pomiaru",
            "Temperatura (°C)",
            dataset,
            false, // legend
            false, // tooltips
            false  // urls
        );
        
        // Konfiguracja wyglądu
        chart.setBackgroundPaint(Color.WHITE);
        chart.getPlot().setBackgroundPaint(Color.WHITE);
        chart.setPadding(new org.jfree.chart.ui.RectangleInsets(10, 10, 10, 10));
        
        // Ustaw czerwony kolor linii
        org.jfree.chart.plot.XYPlot plot = chart.getXYPlot();
        org.jfree.chart.renderer.xy.XYLineAndShapeRenderer renderer = 
            (org.jfree.chart.renderer.xy.XYLineAndShapeRenderer) plot.getRenderer();
        renderer.setSeriesPaint(0, new Color(239, 68, 68)); // Czerwony
        renderer.setSeriesStroke(0, new BasicStroke(2.0f)); // Grubsza linia
        
        // Dodaj kratkę
        plot.setDomainGridlinesVisible(true);
        plot.setDomainGridlinePaint(new Color(200, 200, 200));
        plot.setDomainGridlineStroke(new BasicStroke(0.5f));
        plot.setRangeGridlinesVisible(true);
        plot.setRangeGridlinePaint(new Color(200, 200, 200));
        plot.setRangeGridlineStroke(new BasicStroke(0.5f));
        
        // Konfiguracja osi X - data i godzina
        org.jfree.chart.axis.DateAxis domainAxis = (org.jfree.chart.axis.DateAxis) plot.getDomainAxis();
        domainAxis.setDateFormatOverride(new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm"));
        domainAxis.setLowerMargin(0.02);
        domainAxis.setUpperMargin(0.02);
        domainAxis.setVerticalTickLabels(false);
        
        // Konfiguracja osi Y
        org.jfree.chart.axis.ValueAxis rangeAxis = plot.getRangeAxis();
        rangeAxis.setLowerMargin(0.05);
        rangeAxis.setUpperMargin(0.05);
        
        // Renderuj do PNG
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        org.jfree.chart.ChartUtils.writeChartAsPNG(baos, chart, CHART_WIDTH, CHART_HEIGHT);
        
        return baos.toByteArray();
    }
    
    /**
     * Tworzy komórkę z informacją (label: value)
     */
    private Cell createInfoCell(String label, String value, PdfFont font, PdfFont boldFont) {
        Paragraph p = new Paragraph()
            .add(new com.itextpdf.layout.element.Text(label + " ").setFont(font).setFontSize(9))
            .add(new com.itextpdf.layout.element.Text(value).setFont(boldFont).setFontSize(9));
        
        return new Cell()
            .add(p)
            .setBorder(null)
            .setPadding(3);
    }
    
    /**
     * Tworzy tabelę z danymi pomiarowymi
     */
    private Table createDataTable(List<MeasurementPoint> points, PdfFont font, PdfFont boldFont) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{1, 3, 2}))
            .setWidth(UnitValue.createPercentValue(100))
            .setFontSize(7);
        
        // Nagłówki
        table.addHeaderCell(new Cell().add(new Paragraph("Lp.").setFont(boldFont))
            .setBackgroundColor(ColorConstants.LIGHT_GRAY)
            .setTextAlignment(TextAlignment.CENTER)
            .setPadding(2));
        table.addHeaderCell(new Cell().add(new Paragraph("Data i czas pomiaru").setFont(boldFont))
            .setBackgroundColor(ColorConstants.LIGHT_GRAY)
            .setTextAlignment(TextAlignment.CENTER)
            .setPadding(2));
        table.addHeaderCell(new Cell().add(new Paragraph("Temperatura [°C]").setFont(boldFont))
            .setBackgroundColor(ColorConstants.LIGHT_GRAY)
            .setTextAlignment(TextAlignment.CENTER)
            .setPadding(2));
        
        // Dane
        for (int i = 0; i < points.size(); i++) {
            MeasurementPoint point = points.get(i);
            
            table.addCell(new Cell().add(new Paragraph(String.valueOf(i + 1)).setFont(font))
                .setTextAlignment(TextAlignment.CENTER)
                .setPadding(2));
            table.addCell(new Cell().add(new Paragraph(point.getMeasurementTime().format(DATE_TIME_FORMATTER)).setFont(font))
                .setTextAlignment(TextAlignment.CENTER)
                .setPadding(2));
            table.addCell(new Cell().add(new Paragraph(String.format("%.1f", point.getTemperature())).setFont(font))
                .setTextAlignment(TextAlignment.CENTER)
                .setPadding(2));
        }
        
        return table;
    }
}
