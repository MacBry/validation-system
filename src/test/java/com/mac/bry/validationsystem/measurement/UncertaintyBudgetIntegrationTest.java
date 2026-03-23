package com.mac.bry.validationsystem.measurement;

import com.mac.bry.validationsystem.ValidationSystemApplication;
import com.mac.bry.validationsystem.calibration.Calibration;
import com.mac.bry.validationsystem.calibration.CalibrationPoint;
import com.mac.bry.validationsystem.materialtype.MaterialType;
import com.mac.bry.validationsystem.thermorecorder.ThermoRecorder;
import com.mac.bry.validationsystem.stats.ValidationUncertaintyBudgetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Test integracji systemu budżetu niepewności z aplikacją Spring Boot.
 * Weryfikuje pełny przepływ od kalkulacji po persistencję.
 */
@SpringBootTest(classes = ValidationSystemApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
                "spring.datasource.url=jdbc:h2:mem:testdb",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class UncertaintyBudgetIntegrationTest {

        @Autowired
        private UncertaintyBudgetService uncertaintyBudgetService;

        @Autowired
        private ValidationUncertaintyBudgetService validationUncertaintyBudgetService;

        @Test
        @DisplayName("Should calculate and persist complete uncertainty budget for measurement series")
        void shouldCalculateAndPersistUncertaintyBudgetForSeries() {
                // Given - przygotuj dane testowe
                ThermoRecorder recorder = createTestRecorderWithCalibration();
                MaterialType materialType = createTestMaterialType();
                List<BigDecimal> temperatures = new java.util.ArrayList<>();
                for (int i = 0; i < 35; i++) {
                        double val = 5.00 + ((i % 5) * 0.01 - 0.02);
                        temperatures.add(BigDecimal.valueOf(val).setScale(2, java.math.RoundingMode.HALF_UP));
                }

                // When - oblicz budżet niepewności
                UncertaintyBudget budget = uncertaintyBudgetService.calculateExpandedUncertainty(
                                temperatures, 5, recorder, materialType);

                // Then - sprawdź kompletność budżetu
                assertThat(budget).isNotNull();
                assertThat(budget.getBudgetType()).isEqualTo(UncertaintyBudgetType.SERIES);

                // Weryfikuj komponenty Type A i Type B
                assertThat(budget.getStatisticalUncertainty()).isPositive();
                assertThat(budget.getCalibrationUncertainty()).isEqualTo(0.05); // z mock calibration
                assertThat(budget.getResolutionUncertainty()).isPositive();
                assertThat(budget.getSystematicUncertainty()).isGreaterThanOrEqualTo(0.0);
                assertThat(budget.getStabilityUncertainty()).isPositive();

                // Spatial uncertainty powinien być null dla series-level
                assertThat(budget.getSpatialUncertainty()).isNull();

                // Weryfikuj RSS combination
                double expectedCombined = Math.sqrt(
                                Math.pow(budget.getStatisticalUncertainty(), 2) +
                                                Math.pow(budget.getCalibrationUncertainty(), 2) +
                                                Math.pow(budget.getResolutionUncertainty(), 2) +
                                                Math.pow(budget.getSystematicUncertainty(), 2) +
                                                Math.pow(budget.getStabilityUncertainty(), 2));
                assertThat(budget.getCombinedUncertainty()).isCloseTo(expectedCombined, within(0.001));

                // Weryfikuj expanded uncertainty
                assertThat(budget.getExpandedUncertainty())
                                .isCloseTo(budget.getCoverageFactor() * budget.getCombinedUncertainty(), within(0.001));

                // Weryfikuj metadane GUM
                assertThat(budget.getCoverageFactor()).isEqualTo(2.0);
                assertThat(budget.getConfidenceLevel()).isEqualTo(95.45);
                assertThat(budget.isValid()).isTrue();
        }

        @Test
        @DisplayName("Should calculate validation-level uncertainty budget with spatial component")
        void shouldCalculateValidationLevelUncertaintyBudget() {
                // Given - utwórz mockowe serie pomiarowe z różnymi średnimi (symulacja spatial
                // distribution)
                List<MeasurementSeries> mockSeries = Arrays.asList(
                                createMockSeriesWithUncertaintyBudget(1L, 4.2, createBasicUncertaintyBudget()),
                                createMockSeriesWithUncertaintyBudget(2L, 5.0, createBasicUncertaintyBudget()),
                                createMockSeriesWithUncertaintyBudget(3L, 5.8, createBasicUncertaintyBudget()));

                // When - oblicz budżet walidacyjny
                UncertaintyBudget validationBudget = validationUncertaintyBudgetService
                                .calculateValidationUncertaintyBudget(mockSeries);

                // Then - sprawdź komponenty specyficzne dla walidacji
                assertThat(validationBudget).isNotNull();
                assertThat(validationBudget.getBudgetType()).isEqualTo(UncertaintyBudgetType.VALIDATION);

                // Niepewność przestrzenna powinna być obecna i dodatnia
                assertThat(validationBudget.getSpatialUncertainty()).isNotNull();
                assertThat(validationBudget.getSpatialUncertainty()).isPositive();

                // Verificy że spatial uncertainty ma sens fizyczny (mniejsza niż zakres
                // temperatur)
                double tempRange = 5.8 - 4.2; // 1.6°C
                assertThat(validationBudget.getSpatialUncertainty()).isLessThan(tempRange);

                // Sprawdź dominujące źródło niepewności
                assertThat(validationBudget.getDominantUncertaintySource()).isNotNull();
        }

        @Test
        @DisplayName("Should provide meaningful improvement recommendations")
        void shouldProvideImprovementRecommendations() {
                // Given - budżet z dominującym komponentem kalibracyjnym
                UncertaintyBudget budget = UncertaintyBudget.builder()
                                .statisticalUncertainty(0.01)
                                .calibrationUncertainty(0.1) // dominant component
                                .resolutionUncertainty(0.02)
                                .systematicUncertainty(0.01)
                                .stabilityUncertainty(0.01)
                                .build();

                // When
                String recommendations = validationUncertaintyBudgetService
                                .generateImprovementRecommendations(budget);

                // Then
                assertThat(recommendations).containsIgnoringCase("Calibration");
                assertThat(recommendations).isNotEmpty();
        }

        // =========================================================================
        // HELPER METHODS
        // =========================================================================

        private ThermoRecorder createTestRecorderWithCalibration() {
                ThermoRecorder recorder = ThermoRecorder.builder()
                                .serialNumber("TEST-001")
                                .model("TESTO 175")
                                .resolution(new BigDecimal("0.1"))
                                .status(com.mac.bry.validationsystem.thermorecorder.RecorderStatus.ACTIVE)
                                .build();

                Calibration calibration = Calibration.builder()
                                .calibrationDate(LocalDate.now().minusDays(10))
                                .validUntil(LocalDate.now().plusMonths(11))
                                .certificateNumber("CERT-2026-001")
                                .thermoRecorder(recorder)
                                .build();

                CalibrationPoint point = CalibrationPoint.builder()
                                .temperatureValue(new BigDecimal("5.0"))
                                .systematicError(new BigDecimal("0.02"))
                                .uncertainty(new BigDecimal("0.05")) // ±0.05°C expanded uncertainty
                                .calibration(calibration)
                                .build();

                calibration.getPoints().add(point);
                recorder.getCalibrations().add(calibration);

                return recorder;
        }

        private MaterialType createTestMaterialType() {
                return MaterialType.builder()
                                .name("Test Material")
                                .activationEnergy(new BigDecimal("83.14")) // kJ/mol for vaccines
                                .build();
        }

        private UncertaintyBudget createBasicUncertaintyBudget() {
                return UncertaintyBudget.builder()
                                .statisticalUncertainty(0.01)
                                .calibrationUncertainty(0.05)
                                .resolutionUncertainty(0.03)
                                .systematicUncertainty(0.01)
                                .stabilityUncertainty(0.01)
                                .combinedUncertainty(0.06)
                                .expandedUncertainty(0.12)
                                .coverageFactor(2.0)
                                .confidenceLevel(95.45)
                                .budgetType(UncertaintyBudgetType.SERIES)
                                .build();
        }

        private MeasurementSeries createMockSeriesWithUncertaintyBudget(Long id, Double avgTemp,
                        UncertaintyBudget budget) {
                MeasurementSeries series = new MeasurementSeries();
                series.setId(id);
                series.setAvgTemperature(avgTemp);
                series.setStdDeviation(0.1);
                series.setMeasurementCount(100);
                series.setIsReferenceRecorder(false);
                series.setUncertaintyBudget(budget);
                return series;
        }
}
