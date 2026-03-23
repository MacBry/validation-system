package com.mac.bry.validationsystem.measurement;

import com.mac.bry.validationsystem.calibration.Calibration;
import com.mac.bry.validationsystem.calibration.CalibrationPoint;
import com.mac.bry.validationsystem.materialtype.MaterialType;
import com.mac.bry.validationsystem.thermorecorder.ThermoRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Serwis do obliczania budżetu niepewności zgodnie z GUM (Guide to the Expression of Uncertainty in Measurement).
 *
 * GMP COMPLIANCE FEATURES:
 * - ISO/IEC 17025 compliance - pełny budżet niepewności pomiarowej
 * - Integracja z rzeczywistymi danymi kalibracyjnymi z certyfikatów
 * - Interpolacja niepewności między punktami kalibracyjnymi
 * - Komponenty Typ A (statystyczne) i Typ B (systematyczne)
 * - Root Sum of Squares (RSS) kombinacja niepewności
 * - Współczynnik pokrycia k=2 dla 95% poziomu ufności
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UncertaintyBudgetService {

    /**
     * Oblicza kompletny budżet niepewności dla pojedynczej serii pomiarowej
     *
     * @param temperatureValues lista pomiarów temperatury [°C]
     * @param samplingIntervalMinutes interwał między pomiarami [min]
     * @param recorder rejestrator temperatury z kalibracją
     * @param materialType typ materiału (dla energii aktywacji)
     * @return kompletny budżet niepewności
     */
    public UncertaintyBudget calculateExpandedUncertainty(
            List<BigDecimal> temperatureValues,
            int samplingIntervalMinutes,
            ThermoRecorder recorder,
            MaterialType materialType) {

        if (temperatureValues == null || temperatureValues.isEmpty()) {
            throw new IllegalArgumentException("Lista pomiarów temperatury nie może być pusta");
        }

        int n = temperatureValues.size();

        // Konwersja do double dla obliczeń
        List<Double> temperatures = temperatureValues.stream()
                .map(BigDecimal::doubleValue)
                .collect(Collectors.toList());

        // Podstawowe statystyki
        double mean = temperatures.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = temperatures.stream()
                .mapToDouble(t -> Math.pow(t - mean, 2))
                .sum() / n; // population variance
        double stdDev = Math.sqrt(variance);

        // === KOMPONENTY NIEPEWNOŚCI ===

        // 1. TYP A: Niepewność statystyczna (powtarzalność pomiarów)
        double u_statistical = stdDev / Math.sqrt(n); // standard error of mean

        // 2. TYP B: Niepewność kalibracji (z certyfikatu)
        double u_calibration = getCalibrationUncertainty(recorder, mean);

        // 3. TYP B: Niepewność rozdzielczości (cyfrowa kwantyzacja)
        double u_resolution = getResolutionUncertainty(recorder);

        // 4. TYP B: Niepewność systematyczna (bias z kalibracji)
        double u_systematic = getSystematicUncertainty(recorder, mean);

        // 5. TYP B: Niepewność stabilności długoterminowej (drift)
        double u_stability = calculateStabilityUncertainty(samplingIntervalMinutes, recorder);

        // === KOMBINACJA NIEPEWNOŚCI (RSS) ===
        double u_combined = Math.sqrt(
                Math.pow(u_statistical, 2) +
                Math.pow(u_calibration, 2) +
                Math.pow(u_resolution, 2) +
                Math.pow(u_systematic, 2) +
                Math.pow(u_stability, 2)
        );

        // === NIEPEWNOŚĆ ROZSZERZONA ===
        double coverageFactor = calculateCoverageFactor(n);
        double U_expanded = coverageFactor * u_combined;
        double confidenceLevel = calculateConfidenceLevel(coverageFactor);
        int degreesOfFreedom = calculateDegreesOfFreedom(n, recorder);

        // Notatka obliczeniowa
        String calculationNotes = String.format(
                "Series calculation: n=%d, interval=%dmin, recorder=%s, mean=%.3f°C",
                n, samplingIntervalMinutes, recorder.getSerialNumber(), mean
        );

        log.info("🔬 GUM UNCERTAINTY BUDGET: Recorder {} | Statistical={:.4f} | Calibration={:.4f} | " +
                "Resolution={:.4f} | Systematic={:.4f} | Stability={:.4f} | Combined={:.4f} | Expanded(k={:.1f})={:.4f}°C",
                recorder.getSerialNumber(), u_statistical, u_calibration, u_resolution,
                u_systematic, u_stability, u_combined, coverageFactor, U_expanded);

        return UncertaintyBudget.builder()
                .statisticalUncertainty(u_statistical)
                .calibrationUncertainty(u_calibration)
                .resolutionUncertainty(u_resolution)
                .systematicUncertainty(u_systematic)
                .stabilityUncertainty(u_stability)
                .spatialUncertainty(null) // tylko dla validation-level
                .combinedUncertainty(u_combined)
                .expandedUncertainty(U_expanded)
                .coverageFactor(coverageFactor)
                .confidenceLevel(confidenceLevel)
                .degreesOfFreedom(degreesOfFreedom)
                .budgetType(UncertaintyBudgetType.SERIES)
                .calculationNotes(calculationNotes)
                .build();
    }

    /**
     * Pobiera niepewność kalibracji z certyfikatu wzorcowania rejestratora
     * Interpoluje między punktami kalibracyjnymi jeśli potrzeba
     */
    private double getCalibrationUncertainty(ThermoRecorder recorder, double measuredTemperature) {
        // Znajdź najnowszą ważną kalibrację
        Calibration validCalibration = recorder.getCalibrations().stream()
                .filter(Calibration::isValid)
                .max(Comparator.comparing(Calibration::getCalibrationDate))
                .orElse(null);

        if (validCalibration == null) {
            log.warn("⚠️ BRAK WAŻNEJ KALIBRACJI dla rejestratora {} - używam domyślnej niepewności 1.0°C",
                    recorder.getSerialNumber());
            return 1.0; // penalty za brak kalibracji
        }

        List<CalibrationPoint> points = validCalibration.getPoints();
        if (points.isEmpty()) {
            log.warn("⚠️ BRAK PUNKTÓW KALIBRACYJNYCH w certyfikacie {} - używam 0.5°C",
                    validCalibration.getCertificateNumber());
            return 0.5;
        }

        // Interpolacja lub najbliższy punkt
        double calibrationUncertainty = interpolateCalibrationUncertainty(points, measuredTemperature);

        log.debug("📊 KALIBRACJA: Rejestrator {}, certyfikat {}, temp {:.1f}°C → niepewność {:.3f}°C",
                recorder.getSerialNumber(),
                validCalibration.getCertificateNumber(),
                measuredTemperature,
                calibrationUncertainty);

        return calibrationUncertainty;
    }

    /**
     * Interpoluje niepewność kalibracji między punktami na certyfikacie
     */
    private double interpolateCalibrationUncertainty(List<CalibrationPoint> points, double temperature) {
        if (points.size() == 1) {
            return points.get(0).getUncertainty().doubleValue();
        }

        // Sortuj punkty według temperatury
        List<CalibrationPoint> sortedPoints = points.stream()
                .sorted(Comparator.comparing(p -> p.getTemperatureValue().doubleValue()))
                .collect(Collectors.toList());

        // Znajdź otaczające punkty
        CalibrationPoint lowerPoint = null;
        CalibrationPoint upperPoint = null;

        for (int i = 0; i < sortedPoints.size() - 1; i++) {
            CalibrationPoint p1 = sortedPoints.get(i);
            CalibrationPoint p2 = sortedPoints.get(i + 1);

            double t1 = p1.getTemperatureValue().doubleValue();
            double t2 = p2.getTemperatureValue().doubleValue();

            if (temperature >= t1 && temperature <= t2) {
                lowerPoint = p1;
                upperPoint = p2;
                break;
            }
        }

        // Jeśli temperatura poza zakresem, użyj najbliższego punktu
        if (lowerPoint == null || upperPoint == null) {
            CalibrationPoint closest = sortedPoints.stream()
                    .min(Comparator.comparingDouble(p ->
                            Math.abs(p.getTemperatureValue().doubleValue() - temperature)))
                    .orElse(sortedPoints.get(0));

            double uncertainty = closest.getUncertainty().doubleValue();
            log.debug("🎯 EXTRAPOLACJA: Temp {:.1f}°C poza zakresem, używam punktu {:.1f}°C → U={:.3f}°C",
                    temperature, closest.getTemperatureValue().doubleValue(), uncertainty);
            return uncertainty;
        }

        // INTERPOLACJA LINIOWA
        double t1 = lowerPoint.getTemperatureValue().doubleValue();
        double t2 = upperPoint.getTemperatureValue().doubleValue();
        double u1 = lowerPoint.getUncertainty().doubleValue();
        double u2 = upperPoint.getUncertainty().doubleValue();

        double interpolatedUncertainty = u1 + (u2 - u1) * (temperature - t1) / (t2 - t1);

        log.debug("🔬 INTERPOLACJA: {:.1f}°C (U={:.3f}) ← {:.1f}°C → {:.1f}°C (U={:.3f}) = {:.3f}°C",
                t1, u1, temperature, t2, u2, interpolatedUncertainty);

        return interpolatedUncertainty;
    }

    /**
     * Pobiera niepewność systematyczną (bias) z kalibracji jako komponent niepewności
     */
    private double getSystematicUncertainty(ThermoRecorder recorder, double measuredTemperature) {
        Calibration validCalibration = recorder.getCalibrations().stream()
                .filter(Calibration::isValid)
                .max(Comparator.comparing(Calibration::getCalibrationDate))
                .orElse(null);

        if (validCalibration == null || validCalibration.getPoints().isEmpty()) {
            return 0.0; // brak korekcji systematycznej
        }

        List<CalibrationPoint> points = validCalibration.getPoints();

        // Znajdź najbliższy punkt kalibracyjny
        CalibrationPoint closestPoint = points.stream()
                .min(Comparator.comparingDouble(p ->
                        Math.abs(p.getTemperatureValue().doubleValue() - measuredTemperature)))
                .orElse(points.get(0));

        // Błąd systematyczny z certyfikatu jako niepewność prostokątna
        double systematicError = Math.abs(closestPoint.getSystematicError().doubleValue());

        // Dla rozkładu prostokątnego: u = a/√3, gdzie a = półszerokość
        double u_systematic = systematicError / Math.sqrt(3);

        log.debug("📏 SYSTEMATIC: Rejestrator {}, bias={:.3f}°C → u_sys={:.4f}°C",
                recorder.getSerialNumber(), systematicError, u_systematic);

        return u_systematic;
    }

    /**
     * Oblicza niepewność rozdzielczości cyfrowej rejestratora
     */
    private double getResolutionUncertainty(ThermoRecorder recorder) {
        // Pobierz rozdzielczość z rejestratora (z domyślnymi wartościami)
        BigDecimal resolution = recorder.getResolution();
        double resolutionValue = resolution.doubleValue();

        // Dla cyfrowej rozdzielczości: u_res = resolution / (2 * √3)
        // Rozkład prostokątny ±resolution/2
        double u_resolution = resolutionValue / (2.0 * Math.sqrt(3));

        log.debug("🔢 RESOLUTION: Rejestrator {}, rozdzielczość={:.3f}°C → u_res={:.4f}°C",
                recorder.getSerialNumber(), resolutionValue, u_resolution);

        return u_resolution;
    }

    /**
     * Oblicza niepewność stabilności długoterminowej (drift między kalibracjami)
     */
    private double calculateStabilityUncertainty(int samplingIntervalMinutes, ThermoRecorder recorder) {
        // Oszacowanie driftu na podstawie specyfikacji producenta
        double annualDriftSpec = getRecorderAnnualDrift(recorder); // °C/rok

        // Czas trwania pomiaru w dniach
        double measurementDurationHours = (samplingIntervalMinutes * 24.0) / 60.0; // zakładamy 24h
        double measurementDurationDays = measurementDurationHours / 24.0;

        // Drift podczas pomiaru
        double driftDuringMeasurement = annualDriftSpec * (measurementDurationDays / 365.0);

        // Niepewność prostokątna (równomierny rozkład driftu w czasie)
        double u_stability = driftDuringMeasurement / Math.sqrt(3);

        log.debug("⏱️ STABILITY: Rejestrator {}, drift_annual={:.3f}°C/rok, czas={:.1f}dni → u_stab={:.4f}°C",
                recorder.getSerialNumber(), annualDriftSpec, measurementDurationDays, u_stability);

        return u_stability;
    }

    /**
     * Oszacowanie rocznego driftu rejestratora na podstawie modelu
     */
    private double getRecorderAnnualDrift(ThermoRecorder recorder) {
        String model = recorder.getModel().toLowerCase();

        if (model.contains("testo")) {
            return 0.1; // TESTO: typowo 0.1°C/rok
        } else if (model.contains("precision") || model.contains("pt100")) {
            return 0.05; // Precyzyjne: 0.05°C/rok
        } else {
            return 0.2; // Ogólne: 0.2°C/rok
        }
    }

    /**
     * Oblicza współczynnik pokrycia k na podstawie stopni swobody
     */
    private double calculateCoverageFactor(int n) {
        // Dla dużej liczby pomiarów (n > 30) używaj k=2
        if (n > 30) {
            return 2.0;
        }

        // Dla małych próbek użyj rozkładu t-Studenta
        // Uproszczenie - w pełnej implementacji użyj tablic t-Studenta
        int df = n - 1;
        if (df <= 1) return 12.7; // t(0.975, 1) ≈ 12.7
        if (df <= 2) return 4.3;  // t(0.975, 2) ≈ 4.3
        if (df <= 5) return 2.6;  // t(0.975, 5) ≈ 2.6
        if (df <= 10) return 2.3; // t(0.975, 10) ≈ 2.3
        if (df <= 20) return 2.1; // t(0.975, 20) ≈ 2.1

        return 2.0; // Dla df > 20 zbliża się do rozkładu normalnego
    }

    /**
     * Oblicza poziom ufności na podstawie współczynnika pokrycia
     */
    private double calculateConfidenceLevel(double k) {
        if (Math.abs(k - 2.0) < 0.1) return 95.45; // k=2 → 95.45%
        if (Math.abs(k - 1.0) < 0.1) return 68.27; // k=1 → 68.27%
        if (Math.abs(k - 3.0) < 0.1) return 99.73; // k=3 → 99.73%

        // Przybliżenie dla innych wartości k
        return 100.0 * (1.0 - 2.0 * (1.0 - normalCDF(k)));
    }

    /**
     * Przybliżenie funkcji rozkładu normalnego (CDF)
     */
    private double normalCDF(double x) {
        // Przybliżenie dla x > 0
        return 0.5 * (1.0 + Math.signum(x) * Math.sqrt(1.0 - Math.exp(-2.0 * x * x / Math.PI)));
    }

    /**
     * Oblicza efektywne stopnie swobody (uproszczona wersja Welch-Satterthwaite)
     */
    private int calculateDegreesOfFreedom(int n, ThermoRecorder recorder) {
        // Uproszczona implementacja - w pełnej wersji użyj wzoru Welch-Satterthwaite
        // ν_eff = u_c⁴ / Σ(u_i⁴/ν_i)

        // Dla komponentu statystycznego: ν_A = n - 1
        // Dla komponentów Typ B: ν_B = ∞ (często)

        int statistical_df = Math.max(n - 1, 1);

        // Konserwatywne oszacowanie - głównie ograniczone przez komponent statystyczny
        return Math.max(statistical_df, 10); // minimum 10 stopni swobody
    }
}