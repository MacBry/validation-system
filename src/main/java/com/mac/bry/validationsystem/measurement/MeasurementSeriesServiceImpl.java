package com.mac.bry.validationsystem.measurement;

import com.mac.bry.validationsystem.device.CoolingDevice;
import com.mac.bry.validationsystem.device.CoolingDeviceRepository;
import com.mac.bry.validationsystem.thermorecorder.ThermoRecorder;
import com.mac.bry.validationsystem.thermorecorder.ThermoRecorderRepository;
import com.mac.bry.validationsystem.security.service.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementacja serwisu do zarządzania seriami pomiarowymi
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeasurementSeriesServiceImpl implements MeasurementSeriesService {

    private final MeasurementSeriesRepository seriesRepository;
    private final MeasurementPointRepository pointRepository;
    private final Vi2FileDecoder vi2Decoder;
    private final HtmlTestoFileDecoder htmlDecoder;
    private final CoolingDeviceRepository deviceRepository;
    private final ThermoRecorderRepository recorderRepository;
    private final SecurityService securityService;
    private final UncertaintyBudgetService uncertaintyBudgetService;

    @Override
    @Transactional
    public UploadResult uploadVi2Files(MultipartFile[] files, RecorderPosition position, Long deviceId,
            boolean isReferenceRecorder) throws IOException {
        log.info("Rozpoczęcie przesyłania {} plików .vi2, pozycja: {}, urządzenie: {}, referencyjny: {}",
                files.length, position, deviceId, isReferenceRecorder);

        // Pobranie urządzenia z załadowanymi relacjami
        CoolingDevice device = deviceRepository.findByIdWithRelations(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono urządzenia o ID: " + deviceId));

        List<MeasurementSeries> uploadedSeries = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                log.warn("Plik pusty: {}", file.getOriginalFilename());
                continue;
            }

            String filename = file.getOriginalFilename();

            if (filename == null || (!filename.toLowerCase().endsWith(".vi2")
                    && !filename.toLowerCase().endsWith(".html") && !filename.toLowerCase().endsWith(".htm"))) {
                log.warn("Pominięto plik o nieprawidłowym rozszerzeniu: {}", filename);
                errors.add("Plik " + filename + " nie jest plikiem .vi2 ani .html");
                continue;
            }

            try {
                log.debug("Przetwarzanie pliku: {}", filename);
                byte[] fileData = file.getBytes();
                MeasurementSeries series;

                // KROK 1: Parsowanie pliku i wyciąganie numeru seryjnego
                if (filename.toLowerCase().endsWith(".vi2")) {
                    series = vi2Decoder.parseVi2File(fileData, filename);
                } else {
                    series = htmlDecoder.decode(fileData, filename);
                }

                String serialNumber = series.getRecorderSerialNumber();

                // KROK 2: Sprawdź czy rejestrator istnieje w bazie
                ThermoRecorder recorder = recorderRepository.findBySerialNumber(serialNumber)
                        .orElse(null);

                if (recorder == null) {
                    log.warn("Rejestrator o numerze {} nie istnieje w bazie danych", serialNumber);
                    errors.add(String.format(
                            "Plik %s: rejestrator TESTO o numerze seryjnym %s nie jest zarejestrowany w systemie. " +
                                    "Dodaj rejestrator przed wgraniem pliku.",
                            filename, serialNumber));
                    continue;
                }

                if (recorder.getStatus() == com.mac.bry.validationsystem.thermorecorder.RecorderStatus.UNDER_CALIBRATION) {
                    log.warn("Rejestrator {} jest w trakcie wzorcowania. Blokowanie wgrania pliku.", serialNumber);
                    errors.add(String.format(
                            "Plik %s: Rejestrator o numerze seryjnym %s jest obecnie w statusie 'Wysłano do wzorcowania'. " +
                                    "Nie można go użyć do nowej walidacji do czasu wprowadzenia nowego wzorcowania.",
                            filename, serialNumber));
                    continue;
                }

                if (recorder.getStatus() == com.mac.bry.validationsystem.thermorecorder.RecorderStatus.INACTIVE) {
                    log.warn("Rejestrator {} jest nieaktywny (prawdopodobnie brak ważnego wzorcowania). Blokowanie wgrania pliku.", serialNumber);
                    errors.add(String.format(
                            "Plik %s: Rejestrator o numerze seryjnym %s jest nieaktywny (brak ważnego wzorcowania). " +
                                    "Zaktualizuj dane wzorcowania przed wgraniem plików.",
                            filename, serialNumber));
                    continue;
                }

                log.debug("Znaleziono rejestrator: {} ({})", recorder.getSerialNumber(), recorder.getModel());

                // KROK 3: Połączenie relacji i zapisanie
                series.setIsReferenceRecorder(isReferenceRecorder);
                if (isReferenceRecorder) {
                    series.setRecorderPosition(null); // brak pozycji w siatce
                } else {
                    series.setRecorderPosition(position);
                }
                series.setCoolingDevice(device);
                series.setThermoRecorder(recorder);

                // KROK 4: Obliczenie statystyk (Tmin, Tmax, Tavg, StdDev, MKT)
                calculateStatistics(series);

                // KROK 5: Walidacja zakresu temperatur operacyjnych
                String tempWarning = validateTemperatureRange(series, device);
                if (tempWarning != null) {
                    warnings.add(tempWarning);
                }

                MeasurementSeries savedSeries = seriesRepository.save(series);
                uploadedSeries.add(savedSeries);

                log.info("Pomyślnie przesłano plik: {} (ID: {}, urządzenie: {}, pozycja: {})",
                        filename, savedSeries.getId(), device.getInventoryNumber(), position);

            } catch (Exception e) {
                log.error("Błąd podczas przetwarzania pliku {}: {}", filename, e.getMessage(), e);
                errors.add("Błąd w pliku " + filename + ": " + e.getMessage());
            }
        }

        if (!errors.isEmpty()) {
            String errorMessage = "Napotkano błędy podczas przesyłania plików:\n" +
                    String.join("\n", errors);
            if (uploadedSeries.isEmpty()) {
                throw new IOException(errorMessage);
            }
            log.warn(errorMessage);
        }

        log.info("Zakończono przesyłanie. Pomyślnie: {}, Ostrzeżenia: {}, Błędy: {}",
                uploadedSeries.size(), warnings.size(), errors.size());

        return UploadResult.builder()
                .uploadedSeries(uploadedSeries)
                .warnings(warnings)
                .errors(errors)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeasurementSeriesDto> getAllSeries() {
        log.debug("Pobieranie wszystkich serii pomiarowych");
        return seriesRepository.findAllByOrderByUploadDateDesc().stream()
                .map(MeasurementSeriesDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeasurementSeriesDto> getUnusedSeries() {
        log.debug("Pobieranie NIEużytych serii pomiarowych");
        return seriesRepository.findByUsedInValidationFalseOrderByUploadDateDesc().stream()
                .map(MeasurementSeriesDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeasurementSeriesDto> getAccessibleUnusedSeries() {
        if (securityService.isSuperAdmin()) {
            return getUnusedSeries();
        }

        var cIds = securityService.getAllowedCompanyIds() != null ? securityService.getAllowedCompanyIds()
                : java.util.Collections.<Long>emptySet();
        var dIds = securityService.getDepartmentIdsWithImplicitAccess() != null
                ? securityService.getDepartmentIdsWithImplicitAccess()
                : java.util.Collections.<Long>emptySet();
        var lIds = securityService.getAllowedLaboratoryIds() != null ? securityService.getAllowedLaboratoryIds()
                : java.util.Collections.<Long>emptySet();

        return seriesRepository.findAccessibleAndUnused(false, cIds, dIds, lIds).stream()
                .map(MeasurementSeriesDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeasurementSeriesDto> getUsedSeries() {
        log.debug("Pobieranie użytych serii pomiarowych");
        return seriesRepository.findByUsedInValidationTrueOrderByUploadDateDesc().stream()
                .map(MeasurementSeriesDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeasurementSeriesDto> getAccessibleUsedSeries() {
        if (securityService.isSuperAdmin()) {
            return getUsedSeries();
        }

        var cIds = securityService.getAllowedCompanyIds() != null ? securityService.getAllowedCompanyIds()
                : java.util.Collections.<Long>emptySet();
        var dIds = securityService.getDepartmentIdsWithImplicitAccess() != null
                ? securityService.getDepartmentIdsWithImplicitAccess()
                : java.util.Collections.<Long>emptySet();
        var lIds = securityService.getAllowedLaboratoryIds() != null ? securityService.getAllowedLaboratoryIds()
                : java.util.Collections.<Long>emptySet();

        return seriesRepository.findAllAccessible(false, cIds, dIds, lIds).stream()
                .filter(MeasurementSeries::getUsedInValidation)
                .map(MeasurementSeriesDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public MeasurementSeriesDto getSeriesById(Long id) {
        log.debug("Pobieranie serii pomiarowej o ID: {}", id);
        MeasurementSeries series = seriesRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono serii o ID: " + id));

        // Lazy recalculation for older records missing variance, MKT, CV%, or time
        // stats
        if (series.getVariance() == null || series.getMktTemperature() == null
                || series.getCvPercentage() == null || series.getViolationCount() == null) {
            log.info("Wykryto brakujące statystyki dla serii ID: {}. Trwa ponowne obliczanie...", id);
            // Wywołaj załadowanie punktów i przelicz statystyki
            series.getMeasurementPoints().size();
            calculateStatistics(series);
            seriesRepository.save(series);
        }

        return MeasurementSeriesDto.fromEntity(series);
    }

    @Override
    @Transactional(readOnly = true)
    public MeasurementSeries getSeriesWithPoints(Long id) {
        log.debug("Pobieranie serii pomiarowej z punktami dla ID: {}", id);
        MeasurementSeries series = seriesRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono serii o ID: " + id));

        // Wymuszenie załadowania punktów pomiarowych (fetch join)
        series.getMeasurementPoints().size();

        return series;
    }

    @Override
    @Transactional
    public void deleteSeries(Long id) {
        log.info("Usuwanie serii pomiarowej o ID: {}", id);

        if (!seriesRepository.existsById(id)) {
            throw new IllegalArgumentException("Nie znaleziono serii o ID: " + id);
        }

        seriesRepository.deleteById(id);
        log.info("Pomyślnie usunięto serię o ID: {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeasurementSeriesDto> getSeriesByRecorderSerial(String serialNumber) {
        log.debug("Pobieranie serii dla rejestratora: {}", serialNumber);
        return seriesRepository.findByRecorderSerialNumberOrderByFirstMeasurementTimeDesc(serialNumber)
                .stream()
                .map(MeasurementSeriesDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public void calculateStatistics(MeasurementSeries series) {
        List<MeasurementPoint> points = series.getMeasurementPoints();
        if (points == null || points.isEmpty()) {
            log.warn("Brak punktów pomiarowych dla serii ID: {}. Pomijam statystyki.", series.getId());
            return;
        }

        int n = points.size();
        series.setMeasurementCount(n);
        series.setFirstMeasurementTime(points.get(0).getMeasurementTime());
        series.setLastMeasurementTime(points.get(n - 1).getMeasurementTime());

        double sum = 0.0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        // Dane do MKT
        double gasConstant = 8.314472; // J/(mol*K)
        double activationEnergy = 83.14; // Domyślnie 83.14 kJ/mol

        if (series.getCoolingDevice() != null && series.getCoolingDevice().getMaterialType() != null) {
            java.math.BigDecimal ae = series.getCoolingDevice().getMaterialType().getActivationEnergy();
            if (ae != null) {
                activationEnergy = ae.doubleValue();
            }
        }

        double deltaH = activationEnergy * 1000.0; // Przeliczamy na J/mol
        double mktSum = 0.0;

        // Limity dla statystyk czasowych
        Double minLimit = null;
        Double maxLimit = null;
        if (series.getCoolingDevice() != null) {
            minLimit = series.getCoolingDevice().getMinOperatingTemp();
            maxLimit = series.getCoolingDevice().getMaxOperatingTemp();
        }

        long timeInMinutes = 0;
        long timeOutMinutes = 0;
        int violations = 0;
        long maxViolationDuration = 0;
        long currentViolationDuration = 0;
        boolean inViolation = false;

        for (int i = 0; i < n; i++) {
            MeasurementPoint point = points.get(i);
            double temp = point.getTemperature();
            sum += temp;
            if (temp < min)
                min = temp;
            if (temp > max)
                max = temp;

            // MKT: e^(-deltaH / (R * T_kelvin))
            double tempKelvin = temp + 273.15;
            mktSum += Math.exp(-deltaH / (gasConstant * tempKelvin));

            // Statystyki czasowe - interwał pomiarowy
            if (minLimit != null && maxLimit != null && i < n - 1) {
                long duration = java.time.Duration.between(
                        point.getMeasurementTime(),
                        points.get(i + 1).getMeasurementTime()).toMinutes();

                boolean isOut = temp < minLimit || temp > maxLimit;

                if (isOut) {
                    timeOutMinutes += duration;
                    currentViolationDuration += duration;
                    if (!inViolation) {
                        violations++;
                        inViolation = true;
                    }
                } else {
                    timeInMinutes += duration;
                    if (inViolation) {
                        if (currentViolationDuration > maxViolationDuration) {
                            maxViolationDuration = currentViolationDuration;
                        }
                        currentViolationDuration = 0;
                        inViolation = false;
                    }
                }
            }
        }

        // Finalizacja ostatniego naruszenia
        if (inViolation && currentViolationDuration > maxViolationDuration) {
            maxViolationDuration = currentViolationDuration;
        }

        series.setMinTemperature(min);
        series.setMaxTemperature(max);
        series.setAvgTemperature(sum / n);

        // MKT = (deltaH / R) / -ln(mktSum / n) - 273.15
        if (mktSum > 0) {
            double mktValue = (deltaH / gasConstant) / (-Math.log(mktSum / n)) - 273.15;
            series.setMktTemperature(mktValue);
        }

        // Odchylenie standardowe i wariancja
        double varianceSum = 0.0;
        for (MeasurementPoint point : points) {
            double diff = point.getTemperature() - series.getAvgTemperature();
            varianceSum += diff * diff;
        }
        double var = varianceSum / n;
        series.setVariance(var);
        double stdDev = Math.sqrt(var);
        series.setStdDeviation(stdDev);

        // CV% = (StdDev / Avg) * 100
        if (series.getAvgTemperature() != 0) {
            series.setCvPercentage((stdDev / Math.abs(series.getAvgTemperature())) * 100.0);
        } else {
            series.setCvPercentage(0.0);
        }

        // Mediana, percentyle (wymagają posortowanej listy)
        List<Double> sortedTemps = points.stream()
                .map(MeasurementPoint::getTemperature)
                .sorted()
                .collect(Collectors.toList());

        // Mediana temperatury
        series.setMedianTemperature(calculateMedian(sortedTemps));

        // Interwał pomiarowy (obliczany z pierwszych dwóch pomiarów)
        int intervalMinutes = 0;
        if (n >= 2) {
            long intervalMin = java.time.Duration.between(
                    points.get(0).getMeasurementTime(),
                    points.get(1).getMeasurementTime()).toMinutes();
            intervalMinutes = (int) intervalMin;
            series.setMeasurementIntervalMinutes(intervalMinutes);
        }

        // GUM COMPLIANCE: Pełny budżet niepewności zamiast prostego 2×σ
        UncertaintyBudget uncertaintyBudget = calculateUncertaintyBudget(points, series, intervalMinutes);
        series.setUncertaintyBudget(uncertaintyBudget);
        series.setExpandedUncertainty(uncertaintyBudget.getExpandedUncertainty());

        // Percentyl P5 i P95 (interpolacja liniowa)
        if (n >= 2) {
            series.setPercentile5(calculatePercentile(sortedTemps, 5.0));
            series.setPercentile95(calculatePercentile(sortedTemps, 95.0));
        }

        // Zapisz statystyki czasowe
        series.setTotalTimeInRangeMinutes(timeInMinutes);
        series.setTotalTimeOutOfRangeMinutes(timeOutMinutes);
        series.setViolationCount(violations);
        series.setMaxViolationDurationMinutes(maxViolationDuration);

        // Regresja liniowa dla stabilności
        // T(t) = a + b * t
        // b = (n * sum(t*T) - sum(t) * sum(T)) / (n * sum(t^2) - (sum(t))^2)
        // t to czas w godzinach od startu serii
        double sumT = 0.0;
        double sumTime = 0.0;
        double sumTimeT = 0.0;
        double sumTimeSq = 0.0;
        LocalDateTime start = points.get(0).getMeasurementTime();

        for (MeasurementPoint p : points) {
            double tHours = ChronoUnit.SECONDS.between(start, p.getMeasurementTime()) / 3600.0;
            double temp = p.getTemperature();
            sumT += temp;
            sumTime += tHours;
            sumTimeT += tHours * temp;
            sumTimeSq += tHours * tHours;
        }

        double denominator = (n * sumTimeSq - sumTime * sumTime);
        if (Math.abs(denominator) > 1e-9) {
            double b = (n * sumTimeT - sumTime * sumT) / denominator;
            series.setTrendCoefficient(b);
        } else {
            series.setTrendCoefficient(0.0);
        }

        log.debug("Obliczono statystyki dla serii {}: min={}, max={}, avg={}, mkt={}, violations={}, trend={}",
                series.getId(), min, max, series.getAvgTemperature(), series.getMktTemperature(), violations,
                series.getTrendCoefficient());

        // Analiza Drift vs Spike (Metoda A+ z analizą segmentową)
        if (series.getTrendCoefficient() != null && n > 5) {
            double bOrig = series.getTrendCoefficient();
            double aOrig = (sumT - bOrig * sumTime) / n;

            // 1. Oblicz residuale (odchylenia od linii trendu)
            List<Double> residuals = new ArrayList<>();
            for (MeasurementPoint p : points) {
                double tHours = ChronoUnit.SECONDS.between(start, p.getMeasurementTime()) / 3600.0;
                residuals.add(p.getTemperature() - (aOrig + bOrig * tHours));
            }

            // 2. Metoda MAD (Median Absolute Deviation)
            double medianRes = calculateMedian(residuals);
            List<Double> absDeviations = new ArrayList<>();
            for (double r : residuals) {
                absDeviations.add(Math.abs(r - medianRes));
            }
            double mad = calculateMedian(absDeviations);
            if (mad < 0.01) {
                mad = 0.01;
            }

            // 3. Wykrywanie spike'ów (Próg 3.5 * MAD)
            double threshold = 3.5 * mad;
            List<Integer> spikeIndices = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (absDeviations.get(i) > threshold) {
                    spikeIndices.add(i);
                }
            }

            // 3b. Event padding ±1 (rozszerzenie strefy spike'a o sąsiadów)
            java.util.Set<Integer> paddedSpikes = new java.util.TreeSet<>();
            for (int idx : spikeIndices) {
                if (idx > 0)
                    paddedSpikes.add(idx - 1);
                paddedSpikes.add(idx);
                if (idx < n - 1)
                    paddedSpikes.add(idx + 1);
            }
            int spikeCount = paddedSpikes.size();
            series.setSpikeCount(spikeCount);

            // 4. Regresja skorygowana (bez spike'ów z paddingiem)
            double adjSumT2 = 0, adjSumTime2 = 0, adjSumTimeT2 = 0, adjSumTimeSq2 = 0;
            int nAdj = 0;
            for (int i = 0; i < n; i++) {
                if (!paddedSpikes.contains(i)) {
                    MeasurementPoint p = points.get(i);
                    double tHours = ChronoUnit.SECONDS.between(start, p.getMeasurementTime()) / 3600.0;
                    double temp = p.getTemperature();
                    adjSumT2 += temp;
                    adjSumTime2 += tHours;
                    adjSumTimeT2 += tHours * temp;
                    adjSumTimeSq2 += tHours * tHours;
                    nAdj++;
                }
            }
            double bAdj = bOrig;
            if (nAdj > 5) {
                double denomAdj = (nAdj * adjSumTimeSq2 - adjSumTime2 * adjSumTime2);
                if (Math.abs(denomAdj) > 1e-9) {
                    bAdj = (nAdj * adjSumTimeT2 - adjSumTime2 * adjSumT2) / denomAdj;
                }
            }
            series.setAdjustedTrendCoefficient(bAdj);

            // 5. Obliczenie metryk
            double absBOrig24 = Math.abs(bOrig * 24.0);
            double absBAdj24 = Math.abs(bAdj * 24.0);
            double improvement = absBOrig24 > 1e-6 ? (absBOrig24 - absBAdj24) / absBOrig24 : 0;

            log.info("DIAGNOSTYKA Drift vs Spike [ID:{}]: n={}, spikes={}, MAD={}, " +
                    "threshold={}, orig_drift={}/24h, adj_drift={}/24h, improvement={}%",
                    series.getId(), n, spikeCount,
                    String.format("%.3f", mad), String.format("%.3f", threshold),
                    String.format("%.3f", absBOrig24), String.format("%.3f", absBAdj24),
                    String.format("%.1f", improvement * 100));

            // 6. Klasyfikacja (Metoda A+ z analizą segmentową)
            if (absBOrig24 <= 0.1) {
                series.setDriftClassification("STABLE");
                log.info("Klasyfikacja: STABLE (drift {}°C/24h <= 0.1)", String.format("%.3f", absBOrig24));

            } else if (spikeCount > 0 && absBAdj24 <= 0.1 && improvement > 0.5) {
                // Klasyczna ścieżka SPIKE (regresja skorygowana wystarczyła)
                series.setDriftClassification("SPIKE");
                log.info("Klasyfikacja: SPIKE (klasyczna, {} spike'ów, adj_drift={})",
                        spikeCount, String.format("%.3f", absBAdj24));

            } else if (spikeCount > 0 && absBAdj24 > 0.1) {
                // Regresja skorygowana NIE wystarczyła → analiza segmentowa
                // Sprawdź czy to "level shift" (przesunięcie poziomu po spike)
                int spikeMin = ((java.util.TreeSet<Integer>) paddedSpikes).first();
                int spikeMax = ((java.util.TreeSet<Integer>) paddedSpikes).last();

                // Segment PRZED spike'em
                List<Double> tempsBefore = new ArrayList<>();
                for (int i = 0; i < spikeMin; i++) {
                    tempsBefore.add(points.get(i).getTemperature());
                }
                // Segment PO spike'u
                List<Double> tempsAfter = new ArrayList<>();
                for (int i = spikeMax + 1; i < n; i++) {
                    tempsAfter.add(points.get(i).getTemperature());
                }

                double stdBefore = calculateStdDev(tempsBefore);
                double stdAfter = calculateStdDev(tempsAfter);
                double segmentStabilityThreshold = 0.5; // °C

                boolean beforeStable = tempsBefore.size() >= 3 && stdBefore <= segmentStabilityThreshold;
                boolean afterStable = tempsAfter.size() >= 3 && stdAfter <= segmentStabilityThreshold;
                // Jeśli jeden segment ma za mało danych, traktuj go jako stabilny
                boolean beforeOk = tempsBefore.size() < 3 || beforeStable;
                boolean afterOk = tempsAfter.size() < 3 || afterStable;

                log.info("Analiza segmentowa [ID:{}]: segPrzed(n={}, stdDev={}), segPo(n={}, stdDev={})",
                        series.getId(),
                        tempsBefore.size(), String.format("%.3f", stdBefore),
                        tempsAfter.size(), String.format("%.3f", stdAfter));

                if (beforeOk && afterOk) {
                    // Oba segmenty stabilne → to level shift, nie drift
                    series.setDriftClassification("SPIKE");
                    double avgBefore = tempsBefore.isEmpty() ? 0
                            : tempsBefore.stream().mapToDouble(d -> d).average().orElse(0);
                    double avgAfter = tempsAfter.isEmpty() ? 0
                            : tempsAfter.stream().mapToDouble(d -> d).average().orElse(0);
                    log.info("Klasyfikacja: SPIKE (analiza segmentowa: level shift {}, segmenty stabilne)",
                            String.format("%+.2f°C (%.2f→%.2f)", avgAfter - avgBefore, avgBefore, avgAfter));
                } else {
                    series.setDriftClassification("MIXED");
                    log.info("Klasyfikacja: MIXED (spike'i + niestabilne segmenty, stdPrzed={}, stdPo={})",
                            String.format("%.3f", stdBefore), String.format("%.3f", stdAfter));
                }

            } else {
                series.setDriftClassification("DRIFT");
                log.info("Klasyfikacja: DRIFT (drift {}°C/24h)", String.format("%.3f", absBOrig24));
            }

        } else {
            series.setDriftClassification("STABLE");
            series.setSpikeCount(0);
        }
    }

    private double calculateMedian(List<Double> values) {
        if (values == null || values.isEmpty())
            return 0;
        List<Double> sorted = values.stream().sorted().collect(Collectors.toList());
        int size = sorted.size();
        if (size % 2 == 0) {
            return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
        } else {
            return sorted.get(size / 2);
        }
    }

    private double calculateStdDev(List<Double> values) {
        if (values == null || values.size() < 2)
            return 0;
        double avg = values.stream().mapToDouble(d -> d).average().orElse(0);
        double variance = values.stream().mapToDouble(d -> (d - avg) * (d - avg)).sum() / values.size();
        return Math.sqrt(variance);
    }

    /**
     * Oblicza percentyl metodą interpolacji liniowej.
     * 
     * @param sortedValues posortowana lista wartości
     * @param percentile   percentyl (0-100)
     * @return wartość percentyla
     */
    private double calculatePercentile(List<Double> sortedValues, double percentile) {
        int n = sortedValues.size();
        if (n == 1)
            return sortedValues.get(0);
        double rank = (percentile / 100.0) * (n - 1);
        int lower = (int) Math.floor(rank);
        int upper = (int) Math.ceil(rank);
        if (lower == upper)
            return sortedValues.get(lower);
        double fraction = rank - lower;
        return sortedValues.get(lower) + fraction * (sortedValues.get(upper) - sortedValues.get(lower));
    }

    /**
     * Waliduje czy temperatury w serii mieszczą się w zakresie operacyjnym
     * urządzenia.
     * Zwraca ostrzeżenie jeśli wykryto temperatury poza zakresem, null jeśli OK.
     */

    private String validateTemperatureRange(MeasurementSeries series, CoolingDevice device) {
        // Jeśli urządzenie nie ma zdefiniowanego zakresu - brak walidacji
        if (device.getMinOperatingTemp() == null && device.getMaxOperatingTemp() == null) {
            return null;
        }

        Double minTemp = series.getMinTemperature();
        Double maxTemp = series.getMaxTemperature();
        Double minRange = device.getMinOperatingTemp();
        Double maxRange = device.getMaxOperatingTemp();

        boolean hasViolation = false;
        Double maxDeviation = 0.0;
        String deviationType = "";

        // Sprawdź naruszenie dolnej granicy
        if (minRange != null && minTemp != null && minTemp < minRange) {
            hasViolation = true;
            double deviation = minRange - minTemp;
            if (deviation > maxDeviation) {
                maxDeviation = deviation;
                deviationType = "poniżej dolnej granicy";
            }
        }

        // Sprawdź naruszenie górnej granicy
        if (maxRange != null && maxTemp != null && maxTemp > maxRange) {
            hasViolation = true;
            double deviation = maxTemp - maxRange;
            if (deviation > maxDeviation) {
                maxDeviation = deviation;
                deviationType = "powyżej górnej granicy";
            }
        }

        if (!hasViolation) {
            return null;
        }

        // Formatowanie ostrzeżenia
        StringBuilder warning = new StringBuilder();
        warning.append(String.format("⚠️ OSTRZEŻENIE - Plik %s: ", series.getOriginalFilename()));
        warning.append(String.format("Wykryto temperatury poza zakresem operacyjnym urządzenia. "));

        if (minRange != null && maxRange != null) {
            warning.append(String.format("Poprawny zakres: %.1f°C do %.1f°C. ", minRange, maxRange));
        } else if (minRange != null) {
            warning.append(String.format("Temperatura minimalna: %.1f°C. ", minRange));
        } else if (maxRange != null) {
            warning.append(String.format("Temperatura maksymalna: %.1f°C. ", maxRange));
        }

        warning.append(String.format("Zmierzone temperatury: min %.1f°C, max %.1f°C. ", minTemp, maxTemp));
        warning.append(String.format("Maksymalne odchylenie: %.1f°C %s.", maxDeviation, deviationType));

        log.warn(warning.toString());
        return warning.toString();
    }

    /**
     * Oblicza kompletny budżet niepewności dla serii pomiarowej
     * zgodnie z GUM (Guide to the Expression of Uncertainty in Measurement)
     */
    private UncertaintyBudget calculateUncertaintyBudget(List<MeasurementPoint> points,
            MeasurementSeries series,
            int intervalMinutes) {
        try {
            // Konwersja pomiarów na BigDecimal list
            List<java.math.BigDecimal> temperatureValues = points.stream()
                    .map(p -> {
                        if (p.getTemperature() == null) {
                            log.warn("⚠️ Punkt pomiarowy ID: {} posiada temperaturę NULL. Pomijam.", p.getId());
                            return null;
                        }
                        return java.math.BigDecimal.valueOf(p.getTemperature());
                    })
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());

            if (temperatureValues.isEmpty()) {
                log.error("❌ BRAK POPRAWNYCH TEMPERATUR (wszystkie NULL) dla serii {}", series.getId());
                return createFallbackUncertaintyBudget();
            }

            // Pobierz rejestrator i typ materiału
            ThermoRecorder recorder = series.getThermoRecorder();
            if (recorder == null) {
                log.warn("⚠️ BRAK REJESTRATORA dla serii {} - używam domyślnego budżetu niepewności",
                        series.getId());
                return createFallbackUncertaintyBudget();
            }

            // Typ materiału z urządzenia (jeśli dostępny)
            com.mac.bry.validationsystem.materialtype.MaterialType materialType = null;
            if (series.getCoolingDevice() != null && series.getCoolingDevice().getMaterialType() != null) {
                materialType = series.getCoolingDevice().getMaterialType();
            }

            // Oblicz pełny budżet niepewności
            UncertaintyBudget budget = uncertaintyBudgetService.calculateExpandedUncertainty(
                    temperatureValues,
                    intervalMinutes,
                    recorder,
                    materialType);

            log.info("✅ GMP UNCERTAINTY: Seria {} | Rejestrator {} | Expanded U = {:.4f}°C | Dominant: {}",
                    series.getId(),
                    recorder.getSerialNumber(),
                    budget.getExpandedUncertainty(),
                    budget.getDominantUncertaintySource());

            return budget;

        } catch (Exception e) {
            log.error("❌ BŁĄD KRYTYCZNY obliczania budżetu niepewności dla serii {}: {}. Reason: {}",
                    series.getId(), e.getMessage(),
                    (e.getCause() != null ? e.getCause().getMessage() : "N/A"), e);
            return createFallbackUncertaintyBudget();
        }
    }

    /**
     * Tworzy uproszczony budżet niepewności w przypadku błędów
     */
    private UncertaintyBudget createFallbackUncertaintyBudget() {
        return UncertaintyBudget.builder()
                .statisticalUncertainty(0.1) // domyślna niepewność statystyczna
                .calibrationUncertainty(0.5) // domyślna niepewność kalibracji
                .resolutionUncertainty(0.029) // domyślna niepewność rozdzielczości (0.1/2√3)
                .systematicUncertainty(0.05) // domyślna niepewność systematyczna
                .stabilityUncertainty(0.01) // domyślna niepewność stabilności
                .spatialUncertainty(null) // tylko dla validation level
                .combinedUncertainty(0.51) // √(0.1² + 0.5² + 0.029² + 0.05² + 0.01²)
                .expandedUncertainty(1.02) // k=2
                .coverageFactor(2.0)
                .confidenceLevel(95.45)
                .degreesOfFreedom(10)
                .budgetType(UncertaintyBudgetType.SERIES)
                .calculationNotes("Fallback budget - calculation error occurred")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeasurementSeriesDto> getUnusedSeriesByDevice(Long deviceId) {
        log.debug("Pobieranie NIEużytych serii dla urządzenia ID: {}", deviceId);
        return seriesRepository.findByCoolingDeviceIdAndUsedInValidationFalseOrderByUploadDateDesc(deviceId).stream()
                .map(MeasurementSeriesDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public long countAccessibleSeries() {
        boolean isSuperAdmin = securityService.isSuperAdmin();
        var companyIds = toSafe(securityService.getAllowedCompanyIds());
        var deptIds    = toSafe(securityService.getDepartmentIdsWithImplicitAccess());
        var labIds     = toSafe(securityService.getAllowedLaboratoryIds());
        log.debug("countAccessibleSeries: superAdmin={}, companies={}, depts={}, labs={}",
                isSuperAdmin, companyIds, deptIds, labIds);
        return seriesRepository.countAllAccessible(isSuperAdmin, companyIds, deptIds, labIds);
    }

    @Override
    @Transactional(readOnly = true)
    public long countAccessibleMeasurementPoints() {
        boolean isSuperAdmin = securityService.isSuperAdmin();
        var companyIds = toSafe(securityService.getAllowedCompanyIds());
        var deptIds    = toSafe(securityService.getDepartmentIdsWithImplicitAccess());
        var labIds     = toSafe(securityService.getAllowedLaboratoryIds());
        log.debug("countAccessibleMeasurementPoints: superAdmin={}, companies={}, depts={}, labs={}",
                isSuperAdmin, companyIds, deptIds, labIds);
        return seriesRepository.sumMeasurementPointsAccessible(isSuperAdmin, companyIds, deptIds, labIds);
    }

    /** Zamienia null-kolekcję na pustą listę, by uniknąć błędów zapytań JPA. */
    private java.util.Collection<Long> toSafe(java.util.Collection<Long> col) {
        return col != null ? col : java.util.Collections.emptyList();
    }
}
