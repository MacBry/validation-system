package com.mac.bry.validationsystem.validation;

import com.mac.bry.validationsystem.device.CoolingDevice;
import com.mac.bry.validationsystem.measurement.MeasurementSeries;
import com.mac.bry.validationsystem.measurement.MeasurementSeriesRepository;
import com.mac.bry.validationsystem.measurement.RecorderPosition;
import com.mac.bry.validationsystem.validationplan.ValidationPlanNumber;
import com.mac.bry.validationsystem.validationplan.ValidationPlanNumberRepository;
import com.mac.bry.validationsystem.security.service.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Serwis do zarządzania walidacjami
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValidationService {

    private final ValidationRepository validationRepository;
    private final MeasurementSeriesRepository measurementSeriesRepository;
    private final ValidationDocumentService documentService;
    private final ValidationPlanNumberRepository validationPlanNumberRepository;
    private final SecurityService securityService;
    private final DocumentNumberingService documentNumberingService;

    /**
     * Tworzy nową walidację z wybranych serii pomiarowych
     */
    @Transactional
    public Validation createValidation(List<Long> seriesIds, RecorderPosition controlSensorPosition, DeviceLoadState deviceLoadState) {
        log.info("Tworzenie walidacji z {} serii pomiarowych, stan urządzenia: {}",
                seriesIds.size(), deviceLoadState != null ? deviceLoadState.getDisplayName() : "nieokreślony");

        Validation validation = new Validation();
        validation.setCreatedDate(LocalDateTime.now());
        validation.setStatus(ValidationStatus.DRAFT);

        // Pobierz serie pomiarowe
        for (Long seriesId : seriesIds) {
            MeasurementSeries series = measurementSeriesRepository.findById(seriesId)
                    .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono serii o ID: " + seriesId));

            // WALIDACJA: Sprawdź czy seria nie jest już użyta
            if (series.getUsedInValidation()) {
                throw new IllegalArgumentException(
                        String.format(
                                "Seria pomiarowa '%s' (ID: %d) została już użyta w innej walidacji i nie może być użyta ponownie!",
                                series.getOriginalFilename(), series.getId()));
            }

            // Ustaw urządzenie z pierwszej serii (wszystkie serie powinny być z tego samego
            // urządzenia)
            if (validation.getCoolingDevice() == null && series.getCoolingDevice() != null) {
                validation.setCoolingDevice(series.getCoolingDevice());
                log.info("Ustawiono urządzenie: {} (ID: {})",
                        series.getCoolingDevice().getName(),
                        series.getCoolingDevice().getId());
            }

            validation.addMeasurementSeries(series);

            // Oznacz serię jako użytą w walidacji
            series.setUsedInValidation(true);
            measurementSeriesRepository.save(series);

            log.info("  - Dodano serię: {} (plik: {}, urządzenie: {}, pozycja: {}) - OZNACZONO JAKO UŻYTĄ",
                    series.getId(),
                    series.getOriginalFilename(),
                    series.getCoolingDevice() != null ? series.getCoolingDevice().getInventoryNumber() : "brak",
                    series.getRecorderPosition() != null ? series.getRecorderPosition().getDisplayName() : "brak");
        }

        // WAŻNE: Wygeneruj unikalny numer RPW dla raportu (inkrementacja przy każdym raporcie)
        if (validation.getCoolingDevice() != null) {
            String labAbbrev = resolveLabAbbrev(validation.getCoolingDevice());
            int year = java.time.Year.now().getValue();
            String rpwNumber = documentNumberingService.generateNextNumber("RPW/PR", labAbbrev, year);
            validation.setValidationPlanNumber(rpwNumber);
            log.info("✅ Przypisano numer RPW: {}", rpwNumber);
        } else {
            log.warn("⚠️ BRAK urządzenia - nie można przypisać numeru RPW!");
        }

        // Wylicz średnią temperaturę urządzenia
        validation.calculateAverageDeviceTemperature();
        log.info("Wyliczona średnia temperatura urządzenia: {}", validation.getAverageDeviceTemperature());

        // Ustaw czujnik kontrolujący
        validation.setControlSensorPosition(controlSensorPosition);
        if (controlSensorPosition != null) {
            log.info("Czujnik kontrolujący: {}", controlSensorPosition.getDisplayName());
        }

        // Ustaw stan załadowania urządzenia
        validation.setDeviceLoadState(deviceLoadState);
        if (deviceLoadState != null) {
            log.info("Stan urządzenia podczas walidacji: {}", deviceLoadState.getFullDescription());
        }

        Validation savedValidation = validationRepository.save(validation);

        log.info("========================================");
        log.info("WALIDACJA UTWORZONA - ID: {}", savedValidation.getId());
        log.info("========================================");
        log.info("Urządzenie: {}",
                savedValidation.getCoolingDevice() != null ? savedValidation.getCoolingDevice().getName() : "BRAK!");
        log.info("Data utworzenia: {}", savedValidation.getCreatedDate());
        log.info("Status: {}", savedValidation.getStatus().getDisplayName());
        log.info("Liczba serii: {}", savedValidation.getMeasurementSeries().size());
        log.info("----------------------------------------");

        for (int i = 0; i < savedValidation.getMeasurementSeries().size(); i++) {
            MeasurementSeries series = savedValidation.getMeasurementSeries().get(i);
            log.info("Seria #{}: ", i + 1);
            log.info("  ID: {}", series.getId());
            log.info("  Plik: {}", series.getOriginalFilename());
            log.info("  Rejestrator: {}", series.getRecorderSerialNumber());
            log.info("  Urządzenie: {}",
                    series.getCoolingDevice() != null ? series.getCoolingDevice().getName() : "brak");
            log.info("  Pozycja: {}",
                    series.getRecorderPosition() != null ? series.getRecorderPosition().getDisplayName() : "brak");
            log.info("  Pomiary: {} - {}", series.getFirstMeasurementTime(), series.getLastMeasurementTime());
            log.info("  Temp: min={}, max={}, avg={}", series.getMinTemperature(), series.getMaxTemperature(),
                    series.getAvgTemperature());
            log.info("  Liczba pomiarów: {}", series.getMeasurementCount());
        }

        log.info("========================================");

        // Generuj dokument Word automatycznie
        try {
            log.info("Generowanie dokumentu Word dla walidacji ID: {}", savedValidation.getId());
            byte[] documentBytes = documentService.generateValidationDocument(savedValidation);
            log.info("Dokument Word wygenerowany pomyślnie ({} bajtów)", documentBytes.length);
        } catch (Exception e) {
            log.error("Błąd podczas generowania dokumentu Word: {}", e.getMessage(), e);
        }

        return savedValidation;
    }

    private String resolveLabAbbrev(CoolingDevice device) {
        if (device != null && device.getLaboratory() != null && device.getLaboratory().getAbbreviation() != null) {
            return device.getLaboratory().getAbbreviation();
        }
        return "LAB";
    }

    /**
     * @deprecated Use DocumentNumberingService instead for unique RPW numbers.
     */
    @Deprecated
    private String getOrCreateValidationPlanNumber(CoolingDevice device) {
        int currentYear = java.time.Year.now().getValue();

        ValidationPlanNumber planNumber = validationPlanNumberRepository
                .findByCoolingDeviceAndYear(device, currentYear)
                .orElse(null);

        if (planNumber == null) {
            int nextNumber = validationPlanNumberRepository
                    .findByCoolingDeviceOrderByYearDesc(device)
                    .stream()
                    .mapToInt(ValidationPlanNumber::getPlanNumber)
                    .max()
                    .orElse(0) + 1;

            planNumber = ValidationPlanNumber.builder()
                    .coolingDevice(device)
                    .year(currentYear)
                    .planNumber(nextNumber)
                    .build();

            planNumber = validationPlanNumberRepository.save(planNumber);
        }

        return String.format("%d/%d", planNumber.getPlanNumber(), currentYear);
    }

    /**
     * Pobiera wszystkie walidacje bez filtrowania (tylko dla Super Admin lub
     * wewnętrznych użych).
     */
    @Transactional(readOnly = true)
    public List<Validation> getAllValidations() {
        return validationRepository.findAllByOrderByCreatedDateDesc();
    }

    /**
     * FIX #2: Pobiera walidacje dostępne dla aktualnie zalogowanego użytkownika.
     * Super Admin widzi wszystkie. Inni tylko ze swoich firm/działów.
     */
    @Transactional(readOnly = true)
    public List<Validation> getAllAccessibleValidations() {
        return getAllAccessibleValidations(null, null, null, null);
    }

    /**
     * Pobiera walidacje dostępne dla użytkownika z opcjonalnym filtrowaniem.
     */
    @Transactional(readOnly = true)
    public List<Validation> getAllAccessibleValidations(ValidationStatus status, Long companyId, Long departmentId, Long laboratoryId) {
        boolean isSuperAdmin = securityService.isSuperAdmin();
        var allowedCompanyIds = emptyIfNull(securityService.getAllowedCompanyIds());
        var allowedDeptIds = emptyIfNull(securityService.getDepartmentIdsWithImplicitAccess());
        var allowedLabIds = emptyIfNull(securityService.getAllowedLaboratoryIds());

        log.debug("Pobieranie walidacji z filtrowaniem: status={}, company={}, dept={}, lab={}",
                status, companyId, departmentId, laboratoryId);

        return validationRepository.findAll(
                ValidationSpecifications.filterBy(
                        isSuperAdmin, allowedCompanyIds, allowedDeptIds, allowedLabIds,
                        status, companyId, departmentId, laboratoryId));
    }

    /**
     * Pobiera walidacje dla konkretnego urządzenia.
     */
    @Transactional(readOnly = true)
    public List<Validation> findByDeviceId(Long deviceId) {
        log.debug("Pobieranie walidacji dla urządzenia o ID: {}", deviceId);
        return validationRepository.findByDeviceId(deviceId);
    }

    /**
     * Zwraca liczbę walidacji dostępnych dla aktualnie zalogowanego użytkownika.
     * Filtrowanie jest identyczne jak w getAllAccessibleValidations().
     */
    @Transactional(readOnly = true)
    public long countAccessibleValidations() {
        return getAllAccessibleValidations().size();
    }

    /**
     * Zwraca liczbę walidacji ze statusem COMPLETED dostępnych dla użytkownika.
     */
    @Transactional(readOnly = true)
    public long countCompletedValidations() {
        return getAllAccessibleValidations().stream()
                .filter(v -> v.getStatus() == ValidationStatus.COMPLETED)
                .count();
    }

    /**
     * Zwraca <limit> najnowszych walidacji dostępnych dla użytkownika.
     */
    @Transactional(readOnly = true)
    public List<Validation> findRecentValidations(int limit) {
        return getAllAccessibleValidations().stream()
                .limit(limit)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Zwraca podsumowanie ostatniej walidacji dostępnej dla zalogowanego użytkownika
     * jako płaską mapę gotową do Thymeleaf (bez lazy-loading).
     * Zwraca null jeśli brak walidacji.
     */
    @Transactional(readOnly = true)
    public java.util.Map<String, Object> findLastAccessibleValidationSummary() {
        boolean isSuperAdmin = securityService.isSuperAdmin();
        java.util.Collection<Long> companyIds = emptyIfNull(securityService.getAllowedCompanyIds());
        java.util.Collection<Long> deptIds    = emptyIfNull(securityService.getDepartmentIdsWithImplicitAccess());
        java.util.Collection<Long> labIds     = emptyIfNull(securityService.getAllowedLaboratoryIds());

        List<Validation> results = validationRepository.findLastAccessibleWithSeries(
                isSuperAdmin, companyIds, deptIds, labIds);

        if (results == null || results.isEmpty()) {
            return null;
        }
        Validation last = results.get(0);

        java.util.Map<String, Object> summary = new java.util.LinkedHashMap<>();
        summary.put("validationId",   last.getId());
        summary.put("deviceName",
                last.getCoolingDevice() != null ? last.getCoolingDevice().getName() : "—");
        summary.put("validationDate",
                last.getCreatedDate() != null
                        ? last.getCreatedDate().format(
                            java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                        : "—");

        java.util.List<java.util.Map<String, Object>> seriesMaps = new java.util.ArrayList<>();
        if (last.getMeasurementSeries() != null) {
            for (var ms : last.getMeasurementSeries()) {
                java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("label",    ms.getRecorderPosition() != null ? ms.getRecorderPosition().getDocumentKey() : "?");
                row.put("posLabel", ms.getRecorderPosition() != null ? ms.getRecorderPosition().getDisplayName() : "?");
                row.put("x",        ms.getRecorderPosition() != null ? ms.getRecorderPosition().getX() : 0);
                row.put("y",        ms.getRecorderPosition() != null ? ms.getRecorderPosition().getY() : 0);
                row.put("z",        ms.getRecorderPosition() != null ? ms.getRecorderPosition().getZ() : 0);
                row.put("avg",      ms.getAvgTemperature());
                row.put("min",      ms.getMinTemperature());
                row.put("max",      ms.getMaxTemperature());
                row.put("isRef",    Boolean.TRUE.equals(ms.getIsReferenceRecorder()));
                seriesMaps.add(row);
            }
            // Sortuj: najpierw pozycje wewnętrzne (nie REF), potem REF
            seriesMaps.sort(java.util.Comparator.comparing(m -> Boolean.TRUE.equals(m.get("isRef"))));
        }
        summary.put("series", seriesMaps);
        return summary;
    }

    private java.util.Collection<Long> emptyIfNull(java.util.Collection<Long> col) {
        return col != null ? col : java.util.Collections.emptyList();
    }

    /**
     * Wyszukuje walidacje dostępne dla aktualnie zalogowanego użytkownika.
     */
    @Transactional(readOnly = true)
    public List<Validation> searchAccessibleValidations(String query) {
        boolean isSuperAdmin = securityService.isSuperAdmin();
        var companyIds = emptyIfNull(securityService.getAllowedCompanyIds());
        var deptIds = emptyIfNull(securityService.getDepartmentIdsWithImplicitAccess());
        var labIds = emptyIfNull(securityService.getAllowedLaboratoryIds());

        return validationRepository.searchAccessible(query, isSuperAdmin, companyIds, deptIds, labIds);
    }
}
