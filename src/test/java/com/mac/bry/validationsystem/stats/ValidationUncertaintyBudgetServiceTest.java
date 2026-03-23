package com.mac.bry.validationsystem.stats;

import com.mac.bry.validationsystem.measurement.MeasurementSeries;
import com.mac.bry.validationsystem.measurement.UncertaintyBudget;
import com.mac.bry.validationsystem.measurement.UncertaintyBudgetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ValidationUncertaintyBudgetService Unit Tests - GMP Compliance")
class ValidationUncertaintyBudgetServiceTest {

        @InjectMocks
        private ValidationUncertaintyBudgetService validationUncertaintyBudgetService;

        private List<MeasurementSeries> mockGridSeries;

        @BeforeEach
        void setUp() {
                mockGridSeries = createMockGridSeries();
        }

        @Test
        @DisplayName("Should calculate validation uncertainty budget from multiple series")
        void shouldCalculateValidationUncertaintyBudgetFromMultipleSeries() {
                // When
                UncertaintyBudget validationBudget = validationUncertaintyBudgetService
                                .calculateValidationUncertaintyBudget(mockGridSeries);

                // Then - sprawdź podstawową strukturę
                assertThat(validationBudget).isNotNull();
                assertThat(validationBudget.getBudgetType()).isEqualTo(UncertaintyBudgetType.VALIDATION);

                // Sprawdź czy wszystkie komponenty są obecne
                assertThat(validationBudget.getStatisticalUncertainty()).isPositive();
                assertThat(validationBudget.getCalibrationUncertainty()).isPositive();
                assertThat(validationBudget.getResolutionUncertainty()).isPositive();
                assertThat(validationBudget.getSystematicUncertainty()).isGreaterThanOrEqualTo(0.0);
                assertThat(validationBudget.getStabilityUncertainty()).isPositive();

                // Unikalny komponent dla walidacji - niepewność przestrzenna
                assertThat(validationBudget.getSpatialUncertainty()).isNotNull();
                assertThat(validationBudget.getSpatialUncertainty()).isGreaterThanOrEqualTo(0.0);

                // RSS combination
                double expectedCombined = Math.sqrt(
                                Math.pow(validationBudget.getStatisticalUncertainty(), 2) +
                                                Math.pow(validationBudget.getCalibrationUncertainty(), 2) +
                                                Math.pow(validationBudget.getResolutionUncertainty(), 2) +
                                                Math.pow(validationBudget.getSystematicUncertainty(), 2) +
                                                Math.pow(validationBudget.getStabilityUncertainty(), 2) +
                                                Math.pow(validationBudget.getSpatialUncertainty(), 2));
                assertThat(validationBudget.getCombinedUncertainty()).isCloseTo(expectedCombined, within(0.001));

                // Expanded uncertainty
                assertThat(validationBudget.getExpandedUncertainty())
                                .isCloseTo(2.0 * validationBudget.getCombinedUncertainty(), within(0.001));
        }

        @Test
        @DisplayName("Should calculate spatial uncertainty from temperature distribution")
        void shouldCalculateSpatialUncertaintyFromTemperatureDistribution() {
                // Given - serie z różnymi średnimi temperaturami (symulacja niejednorodności
                // przestrzennej)
                MeasurementSeries coldSpot = createMockSeriesWithBudget(1L, 4.2, 0.1, 100); // cold spot
                MeasurementSeries neutralSpot = createMockSeriesWithBudget(2L, 5.0, 0.1, 100); // neutral
                MeasurementSeries hotSpot = createMockSeriesWithBudget(3L, 5.8, 0.1, 100); // hot spot

                List<MeasurementSeries> spatialSeries = Arrays.asList(coldSpot, neutralSpot, hotSpot);

                // When
                UncertaintyBudget budget = validationUncertaintyBudgetService
                                .calculateValidationUncertaintyBudget(spatialSeries);

                // Then - niepewność przestrzenna powinna odzwierciedlać niejednorodność
                assertThat(budget.getSpatialUncertainty()).isPositive();

                // Sprawdź czy spatial uncertainty ma sens fizyczny
                // σ_spatial = σ(średnie_temperatury) / √n_pozycji
                double tempRange = 5.8 - 4.2; // 1.6°C
                assertThat(budget.getSpatialUncertainty()).isLessThan(tempRange); // musi być rozsądna
        }

        @Test
        @DisplayName("Should use worst-case approach for calibration and resolution uncertainties")
        void shouldUseWorstCaseApproachForSystematicUncertainties() {
                // Given - serie z różnymi niepewnościami kalibracji
                MeasurementSeries series1 = createSeriesWithUncertaintyBudget(1L, 0.03, 0.02, 0.01);
                MeasurementSeries series2 = createSeriesWithUncertaintyBudget(2L, 0.05, 0.03, 0.015); // worst
                MeasurementSeries series3 = createSeriesWithUncertaintyBudget(3L, 0.04, 0.025, 0.012);

                List<MeasurementSeries> seriesWithDifferentUncertainties = Arrays.asList(series1, series2, series3);

                // When
                UncertaintyBudget budget = validationUncertaintyBudgetService
                                .calculateValidationUncertaintyBudget(seriesWithDifferentUncertainties);

                // Then - powinien użyć najgorszych przypadków
                assertThat(budget.getCalibrationUncertainty()).isEqualTo(0.05); // max z [0.03, 0.05, 0.04]
                assertThat(budget.getResolutionUncertainty()).isEqualTo(0.03); // max z [0.02, 0.03, 0.025]
        }

        @Test
        @DisplayName("Should use RSS approach for systematic uncertainty")
        void shouldUseRSSApproachForSystematicUncertainty() {
                // Given - serie z różnymi niepewnościami systematycznymi
                List<MeasurementSeries> series = Arrays.asList(
                                createSeriesWithSystematicUncertainty(1L, 0.01),
                                createSeriesWithSystematicUncertainty(2L, 0.02),
                                createSeriesWithSystematicUncertainty(3L, 0.015));

                // When
                UncertaintyBudget budget = validationUncertaintyBudgetService
                                .calculateValidationUncertaintyBudget(series);

                // Then - systematic uncertainty powinien być √(suma kwadratów / n)
                double expectedSystematic = Math.sqrt((0.01 * 0.01 + 0.02 * 0.02 + 0.015 * 0.015) / 3);
                assertThat(budget.getSystematicUncertainty()).isCloseTo(expectedSystematic, within(0.001));
        }

        @Test
        @DisplayName("Should handle empty series list gracefully")
        void shouldHandleEmptySeriesListGracefully() {
                // When
                UncertaintyBudget budget = validationUncertaintyBudgetService
                                .calculateValidationUncertaintyBudget(Collections.emptyList());

                // Then - powinien zwrócić domyślny budżet
                assertThat(budget).isNotNull();
                assertThat(budget.getBudgetType()).isEqualTo(UncertaintyBudgetType.VALIDATION);
                assertThat(budget.getCalibrationUncertainty()).isEqualTo(0.5); // default for no calibration
                assertThat(budget.getExpandedUncertainty()).isEqualTo(1.0); // k=2 * 0.5
        }

        @Test
        @DisplayName("Should calculate quality index correctly")
        void shouldCalculateQualityIndexCorrectly() {
                // Given
                UncertaintyBudget budget = UncertaintyBudget.builder()
                                .expandedUncertainty(0.1) // 0.1°C
                                .build();

                // When & Then - różne zakresy docelowe
                assertThat(validationUncertaintyBudgetService.calculateUncertaintyQualityIndex(budget, 2.0))
                                .isEqualTo(100.0); // 0.1/2.0 = 5% → doskonała

                assertThat(validationUncertaintyBudgetService.calculateUncertaintyQualityIndex(budget, 1.0))
                                .isEqualTo(80.0); // 0.1/1.0 = 10% → dobra

                assertThat(validationUncertaintyBudgetService.calculateUncertaintyQualityIndex(budget, 0.5))
                                .isEqualTo(60.0); // 0.1/0.5 = 20% → akceptowalna
        }

        @Test
        @DisplayName("Should generate meaningful improvement recommendations")
        void shouldGenerateMeaningfulImprovementRecommendations() {
                // Given - budżet gdzie dominuje niepewność kalibracji
                UncertaintyBudget calibrationDominated = UncertaintyBudget.builder()
                                .statisticalUncertainty(0.01)
                                .calibrationUncertainty(0.1) // dominant
                                .resolutionUncertainty(0.02)
                                .systematicUncertainty(0.01)
                                .stabilityUncertainty(0.01)
                                .spatialUncertainty(0.01)
                                .build();

                // When
                String recommendations = validationUncertaintyBudgetService
                                .generateImprovementRecommendations(calibrationDominated);

                // Then
                assertThat(recommendations).containsIgnoringCase("Calibration");
                assertThat(recommendations).containsAnyOf("kalibracji", "calibration", "interval");
        }

        @Test
        @DisplayName("Should handle series without uncertainty budgets")
        void shouldHandleSeriesWithoutUncertaintyBudgets() {
                // Given - serie bez budżetów niepewności
                List<MeasurementSeries> seriesWithoutBudgets = Arrays.asList(
                                createMockSeries(1L, 5.0, 0.1, 100),
                                createMockSeries(2L, 5.1, 0.1, 100));

                // When
                UncertaintyBudget budget = validationUncertaintyBudgetService
                                .calculateValidationUncertaintyBudget(seriesWithoutBudgets);

                // Then - powinien zwrócić domyślny budżet z ostrzeżeniem
                assertThat(budget).isNotNull();
                assertThat(budget.getCalculationNotes()).contains("no valid measurement series");
        }

        // =========================================================================
        // HELPER METHODS
        // =========================================================================

        private List<MeasurementSeries> createMockGridSeries() {
                return Arrays.asList(
                                createSeriesWithUncertaintyBudget(1L, 0.04, 0.025, 0.01),
                                createSeriesWithUncertaintyBudget(2L, 0.05, 0.03, 0.012),
                                createSeriesWithUncertaintyBudget(3L, 0.045, 0.028, 0.011));
        }

        private MeasurementSeries createMockSeries(Long id, Double avgTemp, Double stdDev, Integer count) {
                MeasurementSeries series = new MeasurementSeries();
                series.setId(id);
                series.setAvgTemperature(avgTemp);
                series.setStdDeviation(stdDev);
                series.setMeasurementCount(count);
                series.setIsReferenceRecorder(false); // grid series

                // Dodaj rejestrator
                com.mac.bry.validationsystem.thermorecorder.ThermoRecorder recorder = new com.mac.bry.validationsystem.thermorecorder.ThermoRecorder();
                recorder.setSerialNumber("TEST-GRID-" + id);
                series.setThermoRecorder(recorder);

                return series;
        }

        private MeasurementSeries createMockSeriesWithBudget(Long id, Double avgTemp, Double stdDev, Integer count) {
                MeasurementSeries series = createMockSeries(id, avgTemp, stdDev, count);

                // Dodaj budżet niepewności
                series.setUncertaintyBudget(UncertaintyBudget.builder()
                                .statisticalUncertainty(0.01)
                                .expandedUncertainty(0.5)
                                .calibrationUncertainty(0.2)
                                .resolutionUncertainty(0.029)
                                .systematicUncertainty(0.1)
                                .stabilityUncertainty(0.05)
                                .build());

                return series;
        }

        private MeasurementSeries createSeriesWithUncertaintyBudget(Long id, Double calUncertainty,
                        Double resUncertainty, Double sysUncertainty) {
                MeasurementSeries series = createMockSeries(id, 5.0, 0.1, 100);

                UncertaintyBudget budget = UncertaintyBudget.builder()
                                .statisticalUncertainty(0.01)
                                .calibrationUncertainty(calUncertainty)
                                .resolutionUncertainty(resUncertainty)
                                .systematicUncertainty(sysUncertainty)
                                .stabilityUncertainty(0.01)
                                .combinedUncertainty(0.05)
                                .expandedUncertainty(0.1)
                                .coverageFactor(2.0)
                                .confidenceLevel(95.45)
                                .budgetType(UncertaintyBudgetType.SERIES)
                                .build();

                series.setUncertaintyBudget(budget);
                return series;
        }

        private MeasurementSeries createSeriesWithSystematicUncertainty(Long id, Double sysUncertainty) {
                return createSeriesWithUncertaintyBudget(id, 0.05, 0.03, sysUncertainty);
        }
}