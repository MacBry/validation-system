package com.mac.bry.validationsystem.validation;

import com.mac.bry.validationsystem.calibration.Calibration;
import com.mac.bry.validationsystem.deviation.DeviationDetectionService;
import com.mac.bry.validationsystem.deviation.DeviationEvent;
import com.mac.bry.validationsystem.measurement.MeasurementPoint;
import com.mac.bry.validationsystem.measurement.MeasurementPointRepository;
import com.mac.bry.validationsystem.measurement.MeasurementSeries;
import com.mac.bry.validationsystem.stats.ValidationSummaryStatsDto;
import com.mac.bry.validationsystem.stats.ValidationSummaryStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Serwis do tworzenia paczek ZIP z dokumentami walidacji
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValidationPackageService {

    private final ValidationDocumentService documentService;
    private final ValidationReportService reportService;
    private final MeasurementPointRepository measurementPointRepository;
    private final ChartPdfService chartPdfService;
    private final SchematWizualnyPdfService schematWizualnyPdfService;
    private final ValidationDocumentTrackingService trackingService;
    private final ValidationSignatureRepository signatureRepository;
    private final ValidationProtocolPdfService protocolPdfService;
    private final RecorderSummaryPdfService recorderSummaryPdfService;
    private final ValidationSummaryStatsService summaryStatsService;
    private final DeviationDetectionService deviationDetectionService;

    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Generuje paczkę ZIP z wszystkimi dokumentami walidacji
     */
    public byte[] generateValidationPackage(Validation validation) throws IOException {
        log.info("Generowanie paczki dokumentów dla walidacji ID: {}", validation.getId());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (ZipOutputStream zos = new ZipOutputStream(baos)) {

            // 1. Dodaj schemat rozmieszczenia rejestratorów (DOCX)
            addSchemaDocument(zos, validation);

            // 2. Dodaj graficzny schemat wizualny rozmieszczenia (PDF)
            addSchematWizualnyPdf(zos, validation);

            // 3. Dodaj certyfikaty kalibracji (PDF)
            addCalibrationCertificates(zos, validation);

            // 4. Dodaj wykresy pomiarów (PDF)
            addMeasurementCharts(zos, validation);

            // 5. Dodaj raport z walidacji (DOCX)
            addValidationReport(zos, validation);

            // 6. Dodaj Protokół Walidacji PDF (kompletny)
            addProtocolPdf(zos, validation);

            // 7. Dodaj raport rejestratorów PDF (zestawienie kalibracji + położeń)
            addRecorderSummaryPdf(zos, validation);

            zos.finish();
        }

        log.info("Paczka dokumentów wygenerowana: {} bajtów", baos.size());
        return baos.toByteArray();
    }

    /**
     * Dodaje schemat rozmieszczenia do ZIP
     */
    private void addSchemaDocument(ZipOutputStream zos, Validation validation) throws IOException {
        log.debug("Dodawanie schematu rozmieszczenia...");

        byte[] schemaBytes = documentService.generateValidationDocument(validation);

        String fileName = String.format("01_Schemat_walidacji_%s_%s.docx",
                validation.getId(),
                validation.getCreatedDate().format(FILE_DATE_FORMAT));

        ZipEntry entry = new ZipEntry(fileName);
        zos.putNextEntry(entry);
        zos.write(schemaBytes);
        zos.closeEntry();

        log.debug("Dodano schemat: {}", fileName);
    }

    /**
     * Dodaje graficzny schemat wizualny (PDF) do ZIP.
     * Jeśli walidacja jest COMPLETED i istnieje podpisany PDF — dołącza go zamiast
     * regenerować.
     * W przeciwnym razie: Faza 2 flow (getOrAllocate → generate →
     * recordGeneration).
     */
    private void addSchematWizualnyPdf(ZipOutputStream zos, Validation validation) throws IOException {
        log.debug("Dodawanie Schematu Wizualnego PDF...");
        try {
            // Faza 3: jeśli walidacja jest COMPLETED i podpisany PDF istnieje — użyj go
            if (validation.getStatus() == ValidationStatus.COMPLETED) {
                var sigOpt = signatureRepository.findByValidationId(validation.getId());
                if (sigOpt.isPresent() && sigOpt.get().getSignedPdfPath() != null) {
                    java.nio.file.Path signedPath = java.nio.file.Path.of(sigOpt.get().getSignedPdfPath());
                    if (java.nio.file.Files.exists(signedPath)) {
                        byte[] signedBytes = java.nio.file.Files.readAllBytes(signedPath);
                        String fileName = String.format("02_Schemat_Wizualny_SIGNED_%s_%s.pdf",
                                validation.getId(),
                                validation.getCreatedDate().format(FILE_DATE_FORMAT));
                        ZipEntry entry = new ZipEntry(fileName);
                        zos.putNextEntry(entry);
                        zos.write(signedBytes);
                        zos.closeEntry();
                        log.debug("Dodano podpisany Schemat Wizualny PDF: {}", fileName);
                        return;
                    } else {
                        log.warn("Faza 3 fallback: podpisany plik nie istnieje na dysku: {}",
                                sigOpt.get().getSignedPdfPath());
                    }
                } else {
                    log.warn(
                            "Faza 3 fallback: brak rekordu podpisu (ValidationSignature) dla COMPLETED walidacji ID: {}",
                            validation.getId());
                }
            }

            // Faza 2 flow: generuj PDF z numerem dokumentu w stopce
            // 1. Zaalokuj numer dokumentu PRZED generowaniem PDF
            ValidationDocument swDoc = trackingService.getOrAllocate(validation, DocumentType.SCHEMAT_WIZUALNY);
            String docNumber = swDoc.getDocumentNumber();
            String generatedBy = trackingService.getCurrentUsername();
            java.time.LocalDateTime now = java.time.LocalDateTime.now();

            // 2. Wygeneruj PDF z numerem w stopce
            byte[] pdfBytes = schematWizualnyPdfService.generateSchematWizualnyPdf(
                    validation, docNumber, generatedBy, now);

            // 3. Zarejestruj zakończoną generację (hash + licznik)
            trackingService.recordGeneration(swDoc.getId(), pdfBytes);

            String fileName = String.format("02_Schemat_Wizualny_%s_%s.pdf",
                    validation.getId(),
                    validation.getCreatedDate().format(FILE_DATE_FORMAT));

            ZipEntry entry = new ZipEntry(fileName);
            zos.putNextEntry(entry);
            zos.write(pdfBytes);
            zos.closeEntry();

            log.debug("Dodano Schemat Wizualny PDF: {} (nr dok.: {})", fileName, docNumber);
        } catch (Exception e) {
            log.error("Błąd podczas generowania Schematu Wizualnego PDF: {}", e.getMessage(), e);
            // Nie blokuj generowania całej paczki z powodu błędu schematu
        }
    }

    /**
     * Dodaje certyfikaty kalibracji do ZIP
     */
    private void addCalibrationCertificates(ZipOutputStream zos, Validation validation) throws IOException {
        log.debug("Dodawanie certyfikatów kalibracji...");

        Set<String> addedCertificates = new HashSet<>();
        int certNumber = 1;

        for (MeasurementSeries series : validation.getMeasurementSeries()) {
            if (series.getThermoRecorder() == null) {
                continue;
            }

            // Pobierz najnowszą kalibrację dla rejestratora
            List<Calibration> calibrations = series.getThermoRecorder().getCalibrations();
            if (calibrations == null || calibrations.isEmpty()) {
                log.warn("Brak kalibracji dla rejestratora: {}", series.getRecorderSerialNumber());
                continue;
            }

            // Posortuj i weź najnowszą
            calibrations.sort((c1, c2) -> c2.getCalibrationDate().compareTo(c1.getCalibrationDate()));
            Calibration latestCalibration = calibrations.get(0);

            String certPath = latestCalibration.getCertificateFilePath();
            if (certPath == null || certPath.isEmpty()) {
                log.warn("Brak ścieżki do certyfikatu dla kalibracji ID: {}", latestCalibration.getId());
                continue;
            }

            // Unikaj duplikatów certyfikatów
            if (addedCertificates.contains(certPath)) {
                continue;
            }

            // Sprawdź czy plik istnieje
            Path filePath = Path.of(certPath);
            if (!Files.exists(filePath)) {
                log.warn("Plik certyfikatu nie istnieje: {}", certPath);
                continue;
            }

            byte[] certBytes = Files.readAllBytes(filePath);

            // Zamiana "/" na "_" w numerze certyfikatu aby uniknąć tworzenia podkatalogów
            String safeCertNumber = latestCalibration.getCertificateNumber().replace("/", "_");

            String fileName = String.format("03_Certyfikat_%02d_Rejestrator_%s_Nr_%s.pdf",
                    certNumber++,
                    series.getRecorderSerialNumber(),
                    safeCertNumber);

            ZipEntry entry = new ZipEntry(fileName);
            zos.putNextEntry(entry);
            zos.write(certBytes);
            zos.closeEntry();

            addedCertificates.add(certPath);
            log.debug("Dodano certyfikat: {}", fileName);
        }
    }

    /**
     * Dodaje wykresy pomiarów do ZIP
     */
    private void addMeasurementCharts(ZipOutputStream zos, Validation validation) throws IOException {
        log.debug("Dodawanie wykresów pomiarów...");

        int chartNumber = 1;

        for (MeasurementSeries series : validation.getMeasurementSeries()) {
            try {
                byte[] chartPdf = chartPdfService.generateChartPdf(series);

                // Użyj polskiej nazwy pozycji
                String positionName = series.getRecorderPosition() != null
                        ? series.getRecorderPosition().getDisplayName().replace(" ", "_").replace("–", "-")
                        : "UNKNOWN";

                String fileName = String.format("04_Wykres_%02d_Seria_%d_%s_%s.pdf",
                        chartNumber++,
                        series.getId(),
                        series.getRecorderSerialNumber(),
                        positionName);

                ZipEntry entry = new ZipEntry(fileName);
                zos.putNextEntry(entry);
                zos.write(chartPdf);
                zos.closeEntry();

                log.debug("Dodano wykres PDF: {}", fileName);
            } catch (Exception e) {
                log.error("Błąd podczas generowania wykresu PDF dla serii {}: {}", series.getId(), e.getMessage(), e);
                // Kontynuuj mimo błędu
            }
        }
    }

    /**
     * Dodaje raport z walidacji do ZIP
     */
    private void addValidationReport(ZipOutputStream zos, Validation validation) throws IOException {
        log.debug("Dodawanie raportu z walidacji...");

        try {
            List<MeasurementSeries> seriesList = new ArrayList<>(validation.getMeasurementSeries());

            byte[] reportBytes = reportService.generateValidationReport(validation, seriesList);

            String fileName = String.format("05_Raport_walidacji_%s_%s.docx",
                    validation.getId(),
                    validation.getCreatedDate().format(FILE_DATE_FORMAT));

            ZipEntry entry = new ZipEntry(fileName);
            zos.putNextEntry(entry);
            zos.write(reportBytes);
            zos.closeEntry();

            log.debug("Dodano raport walidacji: {}", fileName);
        } catch (Exception e) {
            log.error("Błąd podczas generowania raportu walidacji: {}", e.getMessage(), e);
            // Raport walidacji jest kluczowy, więc rzuć wyjątek
            throw new IOException("Nie udało się wygenerować raportu walidacji", e);
        }
    }

    /**
     * Dodaje kompletny Protokół Walidacji (PDF) do ZIP.
     * Zawiera metadane + sekcje A–G + wnioski.
     */
    private void addProtocolPdf(ZipOutputStream zos, Validation validation) throws IOException {
        log.debug("Dodawanie Protokołu Walidacji PDF...");
        try {
            var stats = summaryStatsService.findByValidationId(validation.getId()).orElse(null);
            var deviations = deviationDetectionService.findByValidationId(validation.getId());

            byte[] pdfBytes = protocolPdfService.generateProtocolPdf(validation, stats, deviations);

            String fileName = String.format("06_Protokol_walidacji_%s_%s.pdf",
                    validation.getId(),
                    validation.getCreatedDate().format(FILE_DATE_FORMAT));

            ZipEntry entry = new ZipEntry(fileName);
            zos.putNextEntry(entry);
            zos.write(pdfBytes);
            zos.closeEntry();

            log.debug("Dodano Protokół Walidacji PDF: {}", fileName);
        } catch (Exception e) {
            log.error("Błąd podczas generowania Protokołu Walidacji PDF: {}", e.getMessage(), e);
            // Nie blokuj generowania paczki
        }
    }

    /**
     * Dodaje raport zestawienia rejestratorów (PDF) do ZIP.
     * Zawiera tabelę wszystkich rejestratorów z danymi kalibracji, położeniem i temperaturami.
     */
    private void addRecorderSummaryPdf(ZipOutputStream zos, Validation validation) throws IOException {
        log.debug("Dodawanie raportu rejestratorów PDF...");
        try {
            byte[] pdfBytes = recorderSummaryPdfService.generateRecorderSummaryPdf(validation);

            String fileName = String.format("07_Rejestratory_walidacja_%s_%s.pdf",
                    validation.getId(),
                    validation.getCreatedDate().format(FILE_DATE_FORMAT));

            ZipEntry entry = new ZipEntry(fileName);
            zos.putNextEntry(entry);
            zos.write(pdfBytes);
            zos.closeEntry();

            log.debug("Dodano raport rejestratorów PDF: {}", fileName);
        } catch (Exception e) {
            log.error("Błąd podczas generowania raportu rejestratorów PDF: {}", e.getMessage(), e);
            // Nie blokuj generowania paczki
        }
    }
}
