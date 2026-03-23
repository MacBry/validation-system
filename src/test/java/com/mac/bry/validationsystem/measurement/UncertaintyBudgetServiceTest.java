package com.mac.bry.validationsystem.measurement;

import com.mac.bry.validationsystem.calibration.Calibration;
import com.mac.bry.validationsystem.calibration.CalibrationPoint;
import com.mac.bry.validationsystem.materialtype.MaterialType;
import com.mac.bry.validationsystem.thermorecorder.ThermoRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UncertaintyBudgetService Unit Tests - GMP Compliance")
class UncertaintyBudgetServiceTest {

        @InjectMocks
        private UncertaintyBudgetService uncertaintyBudgetService;

        private ThermoRecorder mockRecorder;
        private MaterialType mockMaterialType;
        private List<BigDecimal> testTemperatures;

        @BeforeEach
        void setUp() {
                // Przygotuj testowy rejestrator z kalibracją
                mockRecorder = createMockRecorderWithCalibration();
                mockMaterialType = createMockMaterialType();

                // Testowe dane temperatury (realistyczne dla chłodziarki 2-8°C)
                // Testowe dane temperatury (realistyczne dla chłodziarki 2-8°C)
                // 31 pomiarów aby przekroczyć próg n > 30 dla k=2.0
                testTemperatures = new java.util.ArrayList<>();
                for (int i = 0; i < 31; i++) {
                        testTemperatures.add(new java.math.BigDecimal("5.0").add(
                                        new java.math.BigDecimal(Math.sin(i)).multiply(new java.math.BigDecimal("0.05"))
                                                        .setScale(2, java.math.RoundingMode.HALF_UP)));
                }
        }

        @Test
        @DisplayName("Should calculate complete uncertainty budget with all components")
        void shouldCalculateCompleteUncertaintyBudget() {
                // When
                UncertaintyBudget budget = uncertaintyBudgetService.calculateExpandedUncertainty(
                                testTemperatures, 5, mockRecorder, mockMaterialType);

                // Then - sprawdź strukturę budżetu
                assertThat(budget).isNotNull();
                assertThat(budget.getBudgetType()).isEqualTo(UncertaintyBudgetType.SERIES);

                // Wszystkie komponenty niepewności muszą być dodatnie
                assertThat(budget.getStatisticalUncertainty()).isPositive();
                assertThat(budget.getCalibrationUncertainty()).isPositive();
                assertThat(budget.getResolutionUncertainty()).isPositive();
                assertThat(budget.getSystematicUncertainty()).isGreaterThanOrEqualTo(0.0);
                assertThat(budget.getStabilityUncertainty()).isPositive();
                assertThat(budget.getSpatialUncertainty()).isNull(); // tylko dla validation level

                // Niepewność kombinowana zgodnie z RSS
                double expectedCombined = Math.sqrt(
                                Math.pow(budget.getStatisticalUncertainty(), 2) +
                                                Math.pow(budget.getCalibrationUncertainty(), 2) +
                                                Math.pow(budget.getResolutionUncertainty(), 2) +
                                                Math.pow(budget.getSystematicUncertainty(), 2) +
                                                Math.pow(budget.getStabilityUncertainty(), 2));
                assertThat(budget.getCombinedUncertainty()).isCloseTo(expectedCombined, within(0.001));

                // Niepewność rozszerzona U = k × u_c
                double expectedExpanded = budget.getCoverageFactor() * budget.getCombinedUncertainty();
                assertThat(budget.getExpandedUncertainty()).isCloseTo(expectedExpanded, within(0.001));
        }

        @Test
        @DisplayName("Should calculate statistical uncertainty as standard error of mean")
        void shouldCalculateStatisticalUncertaintyCorrectly() {
                // Given - dane z dokładnie znanymi statystykami
                List<BigDecimal> preciseData = Arrays.asList(
                                new BigDecimal("5.0"), new BigDecimal("5.1"),
                                new BigDecimal("4.9"), new BigDecimal("5.0"));

                // When
                UncertaintyBudget budget = uncertaintyBudgetService.calculateExpandedUncertainty(
                                preciseData, 5, mockRecorder, mockMaterialType);

                // Then - sprawdź statystyczny komponent
                double mean = 5.0;
                double variance = (0.0 + 0.01 + 0.01 + 0.0) / 4; // population variance
                double stdDev = Math.sqrt(variance);
                double expectedStatistical = stdDev / Math.sqrt(4); // standard error

                assertThat(budget.getStatisticalUncertainty()).isCloseTo(expectedStatistical, within(0.001));
        }

        @Test
        @DisplayName("Should handle calibration uncertainty from certificate")
        void shouldHandleCalibrationUncertaintyFromCertificate() {
                // When
                UncertaintyBudget budget = uncertaintyBudgetService.calculateExpandedUncertainty(
                                testTemperatures, 5, mockRecorder, mockMaterialType);

                // Then - sprawdź czy niepewność kalibracyjna pochodzi z certyfikatu
                assertThat(budget.getCalibrationUncertainty()).isEqualTo(0.05); // z mock calibration point
        }

        @Test
        @DisplayName("Should calculate resolution uncertainty for digital quantization")
        void shouldCalculateResolutionUncertaintyCorrectly() {
                // When
                UncertaintyBudget budget = uncertaintyBudgetService.calculateExpandedUncertainty(
                                testTemperatures, 5, mockRecorder, mockMaterialType);

                // Then - niepewność rozdzielczości dla 0.1°C
                double expectedResolution = 0.1 / (2.0 * Math.sqrt(3)); // rectangular distribution
                assertThat(budget.getResolutionUncertainty()).isCloseTo(expectedResolution, within(0.001));
        }

        @Test
        @DisplayName("Should use appropriate coverage factor based on sample size")
        void shouldUseAppropriateCoverageFactor() {
                // Given - mała próbka
                List<BigDecimal> smallSample = Arrays.asList(
                                new BigDecimal("5.0"), new BigDecimal("5.1"), new BigDecimal("4.9"));

                // When
                UncertaintyBudget budget = uncertaintyBudgetService.calculateExpandedUncertainty(
                                smallSample, 5, mockRecorder, mockMaterialType);

                // Then - dla małej próbki k > 2 (t-Student)
                assertThat(budget.getCoverageFactor()).isGreaterThan(2.0);

                // Given - duża próbka
                UncertaintyBudget largeBudget = uncertaintyBudgetService.calculateExpandedUncertainty(
                                testTemperatures, 5, mockRecorder, mockMaterialType);

                // Then - dla dużej próbki k ≈ 2
                // Then - dla dużej próbki k ≈ 2.0 (n > 30)
                assertThat(largeBudget.getCoverageFactor()).isEqualTo(2.0);
        }

        @Test
        @DisplayName("Should validate budget consistency")
        void shouldValidateBudgetConsistency() {
                // When
                UncertaintyBudget budget = uncertaintyBudgetService.calculateExpandedUncertainty(
                                testTemperatures, 5, mockRecorder, mockMaterialType);

                // Then - budżet musi być spójny
                assertThat(budget.isValid()).isTrue();
                assertThat(budget.getConfidenceLevel()).isGreaterThan(0).isLessThanOrEqualTo(100);
                assertThat(budget.getDegreesOfFreedom()).isPositive();
        }

        @Test
        @DisplayName("Should identify dominant uncertainty source")
        void shouldIdentifyDominantUncertaintySource() {
                // When
                UncertaintyBudget budget = uncertaintyBudgetService.calculateExpandedUncertainty(
                                testTemperatures, 5, mockRecorder, mockMaterialType);

                // Then
                String dominantSource = budget.getDominantUncertaintySource();
                assertThat(dominantSource).isNotNull().isNotEmpty();
                assertThat(dominantSource).containsAnyOf("Statistical", "Calibration", "Resolution", "Systematic",
                                "Stability");
        }

        @Test
        @DisplayName("Should handle missing calibration gracefully")
        void shouldHandleMissingCalibrationGracefully() {
                // Given - rejestrator bez kalibracji
                ThermoRecorder recorderWithoutCalibration = ThermoRecorder.builder()
                                .serialNumber("TEST-NO-CAL")
                                .model("TESTO 175")
                                .build();

                // When
                UncertaintyBudget budget = uncertaintyBudgetService.calculateExpandedUncertainty(
                                testTemperatures, 5, recorderWithoutCalibration, mockMaterialType);

                // Then - powinien użyć domyślnej niepewności
                assertThat(budget.getCalibrationUncertainty()).isEqualTo(1.0); // penalty za brak kalibracji
                assertThat(budget.isValid()).isTrue();
        }

        @Test
        @DisplayName("Should throw exception for empty temperature data")
        void shouldThrowExceptionForEmptyData() {
                // When & Then
                assertThatThrownBy(() -> uncertaintyBudgetService.calculateExpandedUncertainty(
                                Arrays.asList(), 5, mockRecorder, mockMaterialType))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("nie może być pusta");
        }

        @Test
        @DisplayName("Should generate meaningful calculation notes")
        void shouldGenerateMeaningfulCalculationNotes() {
                // When
                UncertaintyBudget budget = uncertaintyBudgetService.calculateExpandedUncertainty(
                                testTemperatures, 5, mockRecorder, mockMaterialType);

                // Then
                assertThat(budget.getCalculationNotes()).isNotNull();
                assertThat(budget.getCalculationNotes()).contains("n=" + testTemperatures.size());
                assertThat(budget.getCalculationNotes()).contains("interval=5min");
                assertThat(budget.getCalculationNotes()).contains("recorder=" + mockRecorder.getSerialNumber());
        }

        // =========================================================================
        // HELPER METHODS
        // =========================================================================

        private ThermoRecorder createMockRecorderWithCalibration() {
                // Kalibration point dla 5°C
                CalibrationPoint point = CalibrationPoint.builder()
                                .temperatureValue(new BigDecimal("5.0"))
                                .systematicError(new BigDecimal("0.02")) // bias +0.02°C
                                .uncertainty(new BigDecimal("0.05")) // ±0.05°C expanded
                                .build();

                // Kalibration
                Calibration calibration = Calibration.builder()
                                .calibrationDate(LocalDate.now().minusMonths(6))
                                .validUntil(LocalDate.now().plusMonths(6))
                                .certificateNumber("CAL-TEST-2024-001")
                                .points(Arrays.asList(point))
                                .build();

                // Recorder z kalibracją
                return ThermoRecorder.builder()
                                .serialNumber("TESTO-123456")
                                .model("TESTO 175")
                                .resolution(new BigDecimal("0.1")) // 0.1°C resolution
                                .calibrations(Arrays.asList(calibration))
                                .build();
        }

        private MaterialType createMockMaterialType() {
                return MaterialType.builder()
                                .name("Vaccines")
                                .activationEnergy(new BigDecimal("83.14")) // kJ/mol
                                .build();
        }
}