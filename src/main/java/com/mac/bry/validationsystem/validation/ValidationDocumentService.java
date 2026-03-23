package com.mac.bry.validationsystem.validation;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Serwis do generowania dokumentów Word dla walidacji
 */
@Slf4j
@Service
public class ValidationDocumentService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Generuje wypełniony dokument walidacji
     */
    public byte[] generateValidationDocument(Validation validation) throws IOException {
        log.info("Generowanie dokumentu walidacji dla ID: {}", validation.getId());

        // Pobierz zasoby z classpath
        ClassPathResource templateResource = new ClassPathResource("templates-docx/schemat_walidacji_template.docx");
        ClassPathResource scriptResource = new ClassPathResource("scripts/fill_validation_template.py");

        if (!templateResource.exists()) {
            throw new IOException(
                    "Szablon dokumentu nie został znaleziony: templates-docx/schemat_walidacji_template.docx");
        }

        if (!scriptResource.exists()) {
            throw new IOException("Skrypt Python nie został znaleziony: scripts/fill_validation_template.py");
        }

        // Skopiuj szablon do pliku tymczasowego (python-docx wymaga ścieżki do pliku)
        Path tempTemplate = Files.createTempFile("template_", ".docx");
        try (InputStream is = templateResource.getInputStream()) {
            Files.copy(is, tempTemplate, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        // Skopiuj skrypt do pliku tymczasowego
        Path tempScript = Files.createTempFile("script_", ".py");
        try (InputStream is = scriptResource.getInputStream()) {
            Files.copy(is, tempScript, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        // Utwórz dane JSON dla skryptu Python
        String jsonData = prepareJsonData(validation);

        // LOGOWANIE JSON DLA DEBUGOWANIA
        log.info("=== GENERATED JSON ===");
        log.info(jsonData);
        log.info("======================");

        // Zapisz dane do tymczasowego pliku
        Path jsonFile = Files.createTempFile("validation_", ".json");
        Files.writeString(jsonFile, jsonData);

        // Wygeneruj unikalną nazwę pliku wyjściowego
        Path outputFile = Files.createTempFile("schemat_", ".docx");

        try {
            // Uruchom skrypt Python
            ProcessBuilder pb = new ProcessBuilder(
                    "python",
                    tempScript.toString(),
                    tempTemplate.toString(),
                    jsonFile.toString(),
                    outputFile.toString());

            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Przeczytaj output
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("Python: {}", line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Błąd generowania dokumentu: exit code " + exitCode);
            }

            // Przeczytaj wygenerowany plik
            byte[] documentBytes = Files.readAllBytes(outputFile);

            log.info("Dokument walidacji wygenerowany: {} bajtów", documentBytes.length);

            return documentBytes;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Przerwano generowanie dokumentu", e);
        } finally {
            // Usuń pliki tymczasowe
            Files.deleteIfExists(tempTemplate);
            Files.deleteIfExists(tempScript);
            Files.deleteIfExists(jsonFile);
            Files.deleteIfExists(outputFile);
        }
    }

    /**
     * Przygotowuje dane JSON dla skryptu Python
     */
    private String prepareJsonData(Validation validation) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");

        // Dane nagłówka - pobierz z pierwszej serii (wszystkie powinny mieć to samo
        // urządzenie)
        if (!validation.getMeasurementSeries().isEmpty()) {
            var firstSeries = validation.getMeasurementSeries().get(0);
            var device = firstSeries.getCoolingDevice();

            if (device != null) {
                json.append("  \"dzial_pracownia\": \"")
                        .append(escape(device.getLaboratory() != null ? device.getLaboratory().getFullName() : "–"))
                        .append("\",\n");
                json.append("  \"nazwa_urzadzenia\": \"")
                        .append(escape(device.getName()))
                        .append("\",\n");
                json.append("  \"numer_inwentarzowy\": \"")
                        .append(escape(device.getInventoryNumber()))
                        .append("\",\n");
                json.append("  \"przechowywany_material\": \"")
                        .append(escape(device.getMaterialName()))
                        .append("\",\n");
            }
        }

        json.append("  \"data_walidacji\": \"")
                .append(validation.getCreatedDate().format(DATE_FORMATTER))
                .append("\",\n");

        // Dane pomiarów - mapowanie po pozycjach
        json.append("  \"pomiary\": {");

        boolean first = true;
        for (var series : validation.getMeasurementSeries()) {
            if (series.getRecorderPosition() != null) {
                if (!first) {
                    json.append(",");
                }
                json.append("\n");
                first = false;

                String positionKey = mapPositionToKey(series.getRecorderPosition());
                json.append("    \"").append(positionKey).append("\": {\n");
                json.append("      \"nr_rejestratora\": \"")
                        .append(escape(series.getRecorderSerialNumber()))
                        .append("\",\n");
                // KLUCZOWA ZMIANA: używamy Locale.US dla kropki jako separatora dziesiętnego
                json.append("      \"temp_max\": ")
                        .append(String.format(Locale.US, "%.1f", series.getMaxTemperature()))
                        .append(",\n");
                json.append("      \"temp_min\": ")
                        .append(String.format(Locale.US, "%.1f", series.getMinTemperature()))
                        .append("\n");
                json.append("    }");
            }
        }

        if (!first) {
            json.append("\n");
        }
        json.append("  }\n");
        json.append("}\n");

        return json.toString();
    }

    /**
     * Mapuje pozycję rejestratora na klucz JSON
     */
    private String mapPositionToKey(com.mac.bry.validationsystem.measurement.RecorderPosition position) {
        return position.getDocumentKey();
    }

    /**
     * Escape JSON strings
     */
    private String escape(String str) {
        if (str == null)
            return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
