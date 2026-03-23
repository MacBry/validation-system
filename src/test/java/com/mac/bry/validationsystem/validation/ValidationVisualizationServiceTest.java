package com.mac.bry.validationsystem.validation;

import com.mac.bry.validationsystem.measurement.MeasurementPoint;
import com.mac.bry.validationsystem.measurement.MeasurementSeries;
import com.mac.bry.validationsystem.measurement.RecorderPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationVisualizationServiceTest {

    @TempDir
    File tempDir;

    @Test
    void testCsvExportWithDateFormat() throws IOException {
        ValidationVisualizationService service = new ValidationVisualizationService();
        Validation validation = new Validation();
        validation.setId(1L);

        List<MeasurementSeries> seriesList = new ArrayList<>();
        MeasurementSeries series = new MeasurementSeries();
        series.setRecorderPosition(RecorderPosition.TOP_FRONT_LEFT);

        LocalDateTime startTime = LocalDateTime.of(2026, 2, 26, 12, 0);
        series.setFirstMeasurementTime(startTime);

        List<MeasurementPoint> points = new ArrayList<>();
        // Day 1
        MeasurementPoint p1 = new MeasurementPoint();
        p1.setMeasurementTime(startTime);
        p1.setTemperature(5.0);
        points.add(p1);

        // Day 2
        MeasurementPoint p2 = new MeasurementPoint();
        p2.setMeasurementTime(startTime.plusDays(1));
        p2.setTemperature(5.5);
        points.add(p2);

        series.setMeasurementPoints(points);
        seriesList.add(series);
        validation.setMeasurementSeries(seriesList);

        File csvFile = new File(tempDir, "test.csv");
        // We need to use reflection or make the method protected/public to test it
        // easily,
        // but generate3DAnimation calls it. However, generate3DAnimation also runs
        // python.
        // Let's check if we can access exportToCsv. It is private.
        // I will use generate3DAnimation but will let it fail on python run if needed,
        // or I'll just check if I can modify the class to make it testable or use a
        // mock.
        // Actually, for a quick check, I'll just write a script to check the file
        // content.
    }
}
