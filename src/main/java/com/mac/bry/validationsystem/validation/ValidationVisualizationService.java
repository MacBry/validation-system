package com.mac.bry.validationsystem.validation;

import com.mac.bry.validationsystem.measurement.MeasurementPoint;
import com.mac.bry.validationsystem.measurement.MeasurementSeries;
import com.mac.bry.validationsystem.measurement.RecorderPosition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class ValidationVisualizationService {

    private static final String SCRIPT_PATH = System.getenv().getOrDefault("PYTHON_SCRIPT_PATH", "scripts") + "/generate_3d_animation.py";
    private static final String PYTHON_EXECUTABLE = System.getenv().getOrDefault("PYTHON_EXECUTABLE", "python");

    public byte[] generate3DAnimation(Validation validation) throws IOException {
        Path tempCsv = Files.createTempFile("validation_" + validation.getId() + "_", ".csv");
        Path tempHtml = Files.createTempFile("animation_" + validation.getId() + "_", ".html");

        try {
            // 1. Eksport danych do CSV
            exportToCsv(validation, tempCsv.toFile());

            // 2. Uruchomienie skryptu Python
            runPythonScript(tempCsv.toAbsolutePath().toString(), tempHtml.toAbsolutePath().toString());

            // 3. Odczyt wygenerowanego HTML
            return Files.readAllBytes(tempHtml);

        } finally {
            // 4. Sprzątanie plików tymczasowych
            try {
                Files.deleteIfExists(tempCsv);
                Files.deleteIfExists(tempHtml);
            } catch (IOException e) {
                log.warn("Nie udało się usunąć plików tymczasowych: {}", e.getMessage());
            }
        }
    }

    private void exportToCsv(Validation validation, File file) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("Czas,Minuta,X,Y,Z,Czujnik,Temperatura\n");

            List<MeasurementSeries> allSeries = validation.getMeasurementSeries();
            if (allSeries.isEmpty())
                return;

            // Znajdź najwcześniejszy punkt startowy, aby zsynchronizować czas (Minuta 0)
            LocalDateTime startTime = allSeries.stream()
                    .map(MeasurementSeries::getFirstMeasurementTime)
                    .filter(Objects::nonNull)
                    .min(LocalDateTime::compareTo)
                    .orElse(LocalDateTime.now());

            for (MeasurementSeries series : allSeries) {
                if (series.getRecorderPosition() == null || Boolean.TRUE.equals(series.getIsReferenceRecorder())) {
                    continue; // Skip reference recorders as they don't have grid coordinates
                }
                int[] coords = mapPositionToCoords(series.getRecorderPosition());
                String sensorName = series.getRecorderPosition().getDisplayName();

                for (MeasurementPoint point : series.getMeasurementPoints()) {
                    long minutes = Duration.between(startTime, point.getMeasurementTime()).toMinutes();
                    String timeStr = point.getMeasurementTime()
                            .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm"));

                    writer.write(String.format(Locale.US, "%s,%d,%d,%d,%d,%s,%.2f\n",
                            timeStr, minutes, coords[0], coords[1], coords[2], sensorName, point.getTemperature()));
                }
            }
        }
    }

    private int[] mapPositionToCoords(RecorderPosition pos) {
        return new int[] { pos.getX(), pos.getY(), pos.getZ() };
    }

    private void runPythonScript(String csvIn, String htmlOut) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(PYTHON_EXECUTABLE, SCRIPT_PATH, csvIn, htmlOut);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (java.util.Scanner s = new java.util.Scanner(process.getInputStream())) {
            while (s.hasNextLine()) {
                String line = s.nextLine();
                output.append(line).append("\n");
                log.debug("Python: {}", line);
            }
        }

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("Skrypt Python zawiódł. Wyjście:\n{}", output);
                throw new IOException("Skrypt Python zakończył się błędem (kod: " + exitCode + "). Sprawdź logi serwera.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Przerwano oczekiwanie na skrypt Python", e);
        }
    }
}
