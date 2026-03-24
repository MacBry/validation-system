package com.mac.bry.validationsystem.validation;

import com.mac.bry.validationsystem.device.CoolingDevice;
import com.mac.bry.validationsystem.measurement.MeasurementPoint;
import com.mac.bry.validationsystem.measurement.MeasurementPointRepository;
import com.mac.bry.validationsystem.measurement.MeasurementSeries;
import com.mac.bry.validationsystem.measurement.RecorderPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChartPdfServiceTest {

    @Mock
    private MeasurementPointRepository measurementPointRepository;

    @InjectMocks
    private ChartPdfService chartPdfService;

    private MeasurementSeries series;
    private List<MeasurementPoint> points;

    @BeforeEach
    void setUp() {
        series = new MeasurementSeries();
        series.setId(1L);
        series.setRecorderSerialNumber("SN12345");
        series.setMeasurementCount(100);
        // Używamy stałej daty zimowej, aby uniknąć problemów z DST (zmiana czasu) w JFreeChart w testach
        series.setFirstMeasurementTime(LocalDateTime.of(2026, 1, 1, 12, 0));
        series.setLastMeasurementTime(series.getFirstMeasurementTime().plusHours(8));
        series.setMinTemperature(2.0);
        series.setMaxTemperature(8.0);
        series.setAvgTemperature(5.0);
        series.setRecorderPosition(RecorderPosition.TOP_FRONT_LEFT);
        series.setOriginalFilename("test_data.csv");

        CoolingDevice device = new CoolingDevice();
        device.setName("Test Fridge");
        device.setInventoryNumber("FR-001");
        series.setCoolingDevice(device);

        points = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            MeasurementPoint point = new MeasurementPoint();
            point.setId((long) i);
            point.setMeasurementTime(series.getFirstMeasurementTime().plusMinutes(i * 5));
            point.setTemperature(5.0 + Math.sin(i / 10.0));
            points.add(point);
        }
    }

    @Test
    void shouldGenerateChartPdf() throws IOException {
        // Given
        when(measurementPointRepository.findBySeriesOrderByMeasurementTimeAsc(any())).thenReturn(points);

        // When
        byte[] pdfBytes = chartPdfService.generateChartPdf(series);

        // Then
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }

    @Test
    void shouldHandleManyPagesCorrectly() throws IOException {
        // Given
        // 2000 punktów to około 40-50 stron tabeli (przy 40-50 wierszach na stronę Landscape)
        List<MeasurementPoint> manyPoints = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            MeasurementPoint point = new MeasurementPoint();
            point.setId((long) i);
            point.setMeasurementTime(series.getFirstMeasurementTime().plusMinutes(i * 5));
            point.setTemperature(5.0 + Math.sin(i / 10.0));
            manyPoints.add(point);
        }
        when(measurementPointRepository.findBySeriesOrderByMeasurementTimeAsc(any())).thenReturn(manyPoints);

        // When
        byte[] pdfBytes = chartPdfService.generateChartPdf(series);

        // Then
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
        
        // Weryfikacja wizualna (opcjonalnie, odkomentuj jeśli potrzebujesz sprawdzić fizycznie plik)
        // java.nio.file.Files.write(java.nio.file.Path.of("C:\\Users\\macie\\Desktop\\Day zero\\test_chart_long.pdf"), pdfBytes);
    }
}
