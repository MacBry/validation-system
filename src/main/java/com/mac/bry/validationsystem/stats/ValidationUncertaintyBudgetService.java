package com.mac.bry.validationsystem.stats;

import com.mac.bry.validationsystem.measurement.MeasurementSeries;
import com.mac.bry.validationsystem.measurement.UncertaintyBudget;
import com.mac.bry.validationsystem.measurement.UncertaintyBudgetType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Serwis do obliczania zagregowanego budżetu niepewności dla całej walidacji.
 * Łączy niepewności ze wszystkich serii siatki używając odpowiednich metod
 * kombinacji.
 *
 * GMP COMPLIANCE FEATURES:
 * - Agregacja niepewności z wielu rejestratorów zgodnie z GUM
 * - Dodatkowy komponent niepewności przestrzennej (niejednorodność urządzenia)
 * - Konserwatywne podejście "worst case" dla komponentów systematycznych
 * - RSS kombinacja dla niezależnych komponentów
 * - Weighted pooled variance dla komponentu statystycznego
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ValidationUncertaintyBudgetService {

    /**
     * Oblicza zagregowany budżet niepewności dla całej walidacji
     * Łączy niepewności ze wszystkich serii siatki (grid series)
     *
     * @param gridSeries lista serii pomiarowych z siatki (bez referencyjnych)
     * @return zagregowany budżet niepewności na poziomie walidacji
     */
    public UncertaintyBudget calculateValidationUncertaintyBudget(List<MeasurementSeries> gridSeries) {

        if (gridSeries == null || gridSeries.isEmpty()) {
            log.warn("⚠️ BRAK SERII SIATKI - zwracam pusty budżet niepewności");
            return createEmptyValidationBudget();
        }

        // Filtruj serie z budżetami niepewności
        List<MeasurementSeries> seriesWithBudgets = gridSeries.stream()
                .filter(s -> s.getUncertaintyBudget() != null)
                .collect(Collectors.toList());

        if (seriesWithBudgets.isEmpty()) {
            log.warn("⚠️ BRAK BUDŻETÓW NIEPEWNOŚCI w seriach siatki");
            return createEmptyValidationBudget();
        }

        // === AGREGACJA KOMPONENTÓW NIEPEWNOŚCI ===

        // 1. KOMPONENT STATYSTYCZNY - Weighted Pooled Variance
        double u_statistical_global = calculateGlobalStatisticalUncertainty(gridSeries);

        // 2. KOMPONENT KALIBRACYJNY - Worst Case Approach
        double u_calibration_max = seriesWithBudgets.stream()
                .mapToDouble(s -> s.getUncertaintyBudget().getCalibrationUncertainty())
                .max().orElse(0.5);

        // 3. KOMPONENT ROZDZIELCZOŚCI - Worst Case Approach
        double u_resolution_max = seriesWithBudgets.stream()
                .mapToDouble(s -> s.getUncertaintyBudget().getResolutionUncertainty())
                .max().orElse(0.029);

        // 4. KOMPONENT SYSTEMATYCZNY - RSS Approach (różne rejestratory)
        double u_systematic_rss = Math.sqrt(seriesWithBudgets.stream()
                .mapToDouble(s -> Math.pow(s.getUncertaintyBudget().getSystematicUncertainty(), 2))
                .sum() / seriesWithBudgets.size());

        // 5. KOMPONENT STABILNOŚCI - Worst Case Approach
        double u_stability_max = seriesWithBudgets.stream()
                .mapToDouble(s -> s.getUncertaintyBudget().getStabilityUncertainty())
                .max().orElse(0.008);

        // 6. KOMPONENT PRZESTRZENNY - Niejednorodność urządzenia
        double u_spatial = calculateSpatialUncertainty(gridSeries);

        // === KOMBINACJA RSS WSZYSTKICH KOMPONENTÓW ===
        double u_combined = Math.sqrt(
                Math.pow(u_statistical_global, 2) +
                        Math.pow(u_calibration_max, 2) +
                        Math.pow(u_resolution_max, 2) +
                        Math.pow(u_systematic_rss, 2) +
                        Math.pow(u_stability_max, 2) +
                        Math.pow(u_spatial, 2));

        // === NIEPEWNOŚĆ ROZSZERZONA ===
        double coverageFactor = 2.0; // k=2 dla walidacji
        double U_expanded = coverageFactor * u_combined;
        double confidenceLevel = 95.45; // dla k=2

        // Całkowita liczba pomiarów i stopnie swobody
        long totalDataPoints = gridSeries.stream().mapToLong(MeasurementSeries::getDataPointsCount).sum();
        int degreesOfFreedom = (int) Math.max(totalDataPoints - gridSeries.size(), 50);

        // Notatka obliczeniowa
        String calculationNotes = String.format(
                "Validation aggregation: %d grid series, %d total points, spatial component included",
                gridSeries.size(), totalDataPoints);

        log.info("🌍 VALIDATION UNCERTAINTY BUDGET: Statistical={:.4f} | Spatial={:.4f} | " +
                "Calibration={:.4f} | Resolution={:.4f} | Systematic={:.4f} | Stability={:.4f} | " +
                "Combined={:.4f} | Expanded(k=2)={:.4f}°C",
                u_statistical_global, u_spatial, u_calibration_max, u_resolution_max,
                u_systematic_rss, u_stability_max, u_combined, U_expanded);

        return UncertaintyBudget.builder()
                .statisticalUncertainty(u_statistical_global)
                .calibrationUncertainty(u_calibration_max)
                .resolutionUncertainty(u_resolution_max)
                .systematicUncertainty(u_systematic_rss)
                .stabilityUncertainty(u_stability_max)
                .spatialUncertainty(u_spatial)
                .combinedUncertainty(u_combined)
                .expandedUncertainty(U_expanded)
                .coverageFactor(coverageFactor)
                .confidenceLevel(confidenceLevel)
                .degreesOfFreedom(degreesOfFreedom)
                .budgetType(UncertaintyBudgetType.VALIDATION)
                .calculationNotes(calculationNotes)
                .build();
    }

    /**
     * Oblicza globalny komponent statystyczny używając weighted pooled variance
     * To jest metodycznie poprawne łączenie wariancji z wielu grup pomiarowych
     */
    private double calculateGlobalStatisticalUncertainty(List<MeasurementSeries> gridSeries) {
        // Filtruj serie z danymi
        List<MeasurementSeries> validSeries = gridSeries.stream()
                .filter(s -> s.getDataPointsCount() != null && s.getDataPointsCount() > 0)
                .filter(s -> s.getStdDeviation() != null)
                .collect(Collectors.toList());

        if (validSeries.isEmpty()) {
            log.warn("⚠️ BRAK WAŻNYCH SERII do obliczenia globalnej niepewności statystycznej");
            return 0.1; // domyślna wartość
        }

        // Weighted pooled variance (tożsamość Steinera)
        long totalN = validSeries.stream().mapToLong(MeasurementSeries::getDataPointsCount).sum();
        double pooledVariance = validSeries.stream()
                .mapToDouble(s -> s.getDataPointsCount() * Math.pow(s.getStdDeviation(), 2))
                .sum() / totalN;

        double globalStdDev = Math.sqrt(pooledVariance);
        double u_statistical_global = globalStdDev / Math.sqrt(totalN);

        log.debug("📊 GLOBAL STATISTICAL: {} serii, {} punktów, pooled σ={:.4f}, u_stat={:.4f}°C",
                validSeries.size(), totalN, globalStdDev, u_statistical_global);

        return u_statistical_global;
    }

    /**
     * Oblicza niepewność przestrzenną - różnice między pozycjami w urządzeniu
     * To jest unikalny komponent dla walidacji, nieobecny w pojedynczych seriach
     */
    private double calculateSpatialUncertainty(List<MeasurementSeries> gridSeries) {
        // Filtruj serie z średnimi temperaturami
        List<Double> avgTemperatures = gridSeries.stream()
                .filter(s -> s.getAvgTemperature() != null)
                .map(MeasurementSeries::getAvgTemperature)
                .collect(Collectors.toList());

        if (avgTemperatures.size() < 2) {
            log.debug("🏠 SPATIAL: Mniej niż 2 pozycje - brak niepewności przestrzennej");
            return 0.0;
        }

        // Statystyki przestrzenne
        double spatialMean = avgTemperatures.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double spatialVariance = avgTemperatures.stream()
                .mapToDouble(t -> Math.pow(t - spatialMean, 2))
                .sum() / avgTemperatures.size();
        double spatialStdDev = Math.sqrt(spatialVariance);

        // Przestrzenna niepewność = spatial std dev / sqrt(n_positions)
        // Reprezentuje niepewność gdzie w urządzeniu ma miejsce pomiar
        double u_spatial = spatialStdDev / Math.sqrt(avgTemperatures.size());

        log.debug("🏠 SPATIAL: {} pozycji, zakres temp {:.3f}-{:.3f}°C, σ_spatial={:.4f}, u_spatial={:.4f}°C",
                avgTemperatures.size(),
                avgTemperatures.stream().mapToDouble(Double::doubleValue).min().orElse(0.0),
                avgTemperatures.stream().mapToDouble(Double::doubleValue).max().orElse(0.0),
                spatialStdDev, u_spatial);

        return u_spatial;
    }

    /**
     * Tworzy pusty budżet niepewności dla przypadku braku danych
     */
    private UncertaintyBudget createEmptyValidationBudget() {
        return UncertaintyBudget.builder()
                .statisticalUncertainty(0.0)
                .calibrationUncertainty(0.5) // domyślna dla braku kalibracji
                .resolutionUncertainty(0.029) // domyślna dla 0.1°C resolution
                .systematicUncertainty(0.0)
                .stabilityUncertainty(0.0)
                .spatialUncertainty(0.0)
                .combinedUncertainty(0.5)
                .expandedUncertainty(1.0) // k=2
                .coverageFactor(2.0)
                .confidenceLevel(95.45)
                .degreesOfFreedom(10)
                .budgetType(UncertaintyBudgetType.VALIDATION)
                .calculationNotes("Empty budget - no valid measurement series")
                .build();
    }

    /**
     * Oblicza wskaźnik jakości budżetu niepewności walidacji
     * Pomaga ocenić czy niepewność jest odpowiednia dla procesu
     *
     * @param validationBudget budżet niepewności walidacji
     * @param targetRange      docelowy zakres temperatury (np. 2-8°C)
     * @return wskaźnik jakości 0-100%
     */
    public double calculateUncertaintyQualityIndex(UncertaintyBudget validationBudget, double targetRange) {
        if (validationBudget == null)
            return 0.0;

        double expandedUncertainty = validationBudget.getExpandedUncertainty();

        // Niepewność powinna być < 10% zakresu docelowego dla dobrej jakości
        double uncertaintyRatio = expandedUncertainty / targetRange;

        if (uncertaintyRatio <= 0.05)
            return 100.0; // Doskonała (≤5%)
        if (uncertaintyRatio <= 0.10)
            return 80.0; // Dobra (5-10%)
        if (uncertaintyRatio <= 0.20)
            return 60.0; // Akceptowalna (10-20%)
        if (uncertaintyRatio <= 0.50)
            return 40.0; // Marginalna (20-50%)

        return 20.0; // Słaba (>50%)
    }

    /**
     * Generuje rekomendacje do poprawy budżetu niepewności
     */
    public String generateImprovementRecommendations(UncertaintyBudget budget) {
        if (budget == null)
            return "Brak budżetu niepewności do analizy";

        StringBuilder recommendations = new StringBuilder();
        String dominantSource = budget.getDominantUncertaintySource();

        recommendations.append("Dominujący komponent: ").append(dominantSource).append(". ");

        // Rekomendacje na podstawie głównego źródła niepewności
        if (dominantSource.contains("Statistical")) {
            recommendations.append("Zwiększ liczbę pomiarów lub wydłuż czas próbkowania. ");
        } else if (dominantSource.contains("Calibration")) {
            recommendations.append("Użyj rejestratorów z lepszą kalibracją lub skróć interval między kalibracjami. ");
        } else if (dominantSource.contains("Resolution")) {
            recommendations.append("Użyj rejestratorów o wyższej rozdzielczości. ");
        } else if (dominantSource.contains("Spatial")) {
            recommendations.append("Poprawa izolacji urządzenia lub optymalizacja rozkładu temperatury. ");
        } else if (dominantSource.contains("Stability")) {
            recommendations.append("Częstsze kalibracje lub stabilizacja środowiska pomiaru. ");
        }

        return recommendations.toString();
    }
}