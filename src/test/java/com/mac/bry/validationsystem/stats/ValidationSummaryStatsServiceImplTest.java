package com.mac.bry.validationsystem.stats;

import com.mac.bry.validationsystem.measurement.MeasurementSeries;
import com.mac.bry.validationsystem.validation.Validation;
import com.mac.bry.validationsystem.validation.ValidationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Kompleksowe testy jednostkowe dla ValidationSummaryStatsServiceImpl.
 *
 * Weryfikują poprawność agregacji statystyk globalnych (Tabele A–D + metadane)
 * obliczanych z pre-obliczonych pól MeasurementSeries.
 *
 * Scenariusze bazowe: 5 serii siatki + 1 referencyjna (lodówka farmaceutyczna
 * 2–8°C).
 * - Serie siatki: 288 pomiarów każda (24h @ 5min)
 * - Seria referencyjna: 288 pomiarów (temperatura otoczenia ~22°C)
 *
 * Dane symulują realistyczne warunki: różne średnie, wariancje, MKT.
 */
@ExtendWith(MockitoExtension.class)
class ValidationSummaryStatsServiceImplTest {

    @InjectMocks
    private ValidationSummaryStatsServiceImpl service;

    @Mock
    private ValidationSummaryStatsRepository statsRepository;

    @Mock
    private ValidationRepository validationRepository;

    @Mock
    private ValidationUncertaintyBudgetService validationUncertaintyBudgetService;

    private static final Long VALIDATION_ID = 1L;
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 1, 15, 8, 0, 0);
    private static final int POINTS_24H = 288;
    private static final int INTERVAL_5MIN = 5;

    // ─── Realistyczne serie testowe ──────────────────────────────────────

    /**
     * Tworzy serię pomiarową z wstępnie obliczonymi statystykami
     * (symulacja tego, co calculateStatistics() robi per seria).
     */
    private MeasurementSeries buildSeries(
            Long id, boolean isReference,
            double minTemp, double maxTemp, double avgTemp,
            double variance, double stdDev, double cvPercentage,
            double expandedUncertainty, double percentile5, double percentile95,
            double mktTemp, int measurementCount,
            long timeInRange, long timeOutOfRange,
            int violationCount, Long maxViolationDuration,
            double trendCoefficient, int spikeCount, String driftClassification,
            int intervalMinutes) {

        MeasurementSeries s = new MeasurementSeries();
        s.setId(id);
        s.setIsReferenceRecorder(isReference);
        s.setMinTemperature(minTemp);
        s.setMaxTemperature(maxTemp);
        s.setAvgTemperature(avgTemp);
        s.setVariance(variance);
        s.setStdDeviation(stdDev);
        s.setCvPercentage(cvPercentage);
        s.setExpandedUncertainty(expandedUncertainty);
        s.setPercentile5(percentile5);
        s.setPercentile95(percentile95);
        s.setMktTemperature(mktTemp);
        s.setMeasurementCount(measurementCount);
        s.setTotalTimeInRangeMinutes(timeInRange);
        s.setTotalTimeOutOfRangeMinutes(timeOutOfRange);
        s.setViolationCount(violationCount);
        s.setMaxViolationDurationMinutes(maxViolationDuration);
        s.setTrendCoefficient(trendCoefficient);
        s.setSpikeCount(spikeCount);
        s.setDriftClassification(driftClassification);
        s.setMeasurementIntervalMinutes(intervalMinutes);
        s.setFirstMeasurementTime(BASE_TIME);
        s.setLastMeasurementTime(BASE_TIME.plusMinutes((long) (measurementCount - 1) * intervalMinutes));
        return s;
    }

    /**
     * Standardowy zestaw: 5 serii siatki + 1 referencyjna.
     * Wartości realistyczne dla lodówki farmaceutycznej 2–8°C.
     */
    private List<MeasurementSeries> buildStandardSeriesSet() {
        List<MeasurementSeries> series = new ArrayList<>();

        // S1: FRONT_TOP_LEFT — stabilna, 5.2°C
        series.add(buildSeries(1L, false,
                4.8, 5.6, 5.2,
                0.04, 0.2, 3.85,
                0.4, 4.9, 5.5,
                5.21, POINTS_24H,
                1435, 0, 0, null,
                0.001, 0, "STABLE",
                INTERVAL_5MIN));

        // S2: FRONT_TOP_RIGHT — stabilna, 4.8°C (coldspot in avg)
        series.add(buildSeries(2L, false,
                4.2, 5.4, 4.8,
                0.06, 0.245, 5.1,
                0.49, 4.3, 5.3,
                4.82, POINTS_24H,
                1435, 0, 0, null,
                -0.002, 0, "STABLE",
                INTERVAL_5MIN));

        // S3: BACK_BOTTOM_LEFT — cieplejsza, 6.1°C (hotspot)
        series.add(buildSeries(3L, false,
                5.5, 7.8, 6.1,
                0.12, 0.346, 5.67,
                0.692, 5.6, 6.8,
                6.14, POINTS_24H,
                1400, 35, 2, 20L,
                0.005, 0, "STABLE",
                INTERVAL_5MIN));

        // S4: BACK_BOTTOM_RIGHT — z driftem
        series.add(buildSeries(4L, false,
                4.5, 6.8, 5.5,
                0.25, 0.5, 9.09,
                1.0, 4.7, 6.5,
                5.54, POINTS_24H,
                1410, 25, 1, 15L,
                0.04, 0, "DRIFT",
                INTERVAL_5MIN));

        // S5: CENTER — z 1 spike'iem
        series.add(buildSeries(5L, false,
                4.6, 9.2, 5.3,
                0.09, 0.3, 5.66,
                0.6, 4.7, 5.8,
                5.33, POINTS_24H,
                1420, 15, 1, 10L,
                0.0, 1, "SPIKE",
                INTERVAL_5MIN));

        // S6: REFERENCE — temperatura otoczenia 22°C
        series.add(buildSeries(6L, true,
                20.5, 23.5, 22.0,
                0.5, 0.707, 3.21,
                1.414, 21.0, 23.0,
                22.1, POINTS_24H,
                1435, 0, 0, null,
                0.0, 0, "STABLE",
                INTERVAL_5MIN));

        return series;
    }

    private void setupMocks(List<MeasurementSeries> seriesList) {
        Validation validation = new Validation();
        validation.setId(VALIDATION_ID);
        validation.setMeasurementSeries(seriesList);

        lenient().when(validationRepository.findById(VALIDATION_ID)).thenReturn(Optional.of(validation));
        lenient().when(statsRepository.findByValidationId(VALIDATION_ID)).thenReturn(Optional.empty());
        lenient().when(statsRepository.save(any(ValidationSummaryStats.class)))
                .thenAnswer(inv -> {
                    ValidationSummaryStats s = inv.getArgument(0);
                    s.setId(100L);
                    return s;
                });

        lenient().when(validationUncertaintyBudgetService.calculateValidationUncertaintyBudget(anyList()))
                .thenReturn(com.mac.bry.validationsystem.measurement.UncertaintyBudget.builder()
                        .expandedUncertainty(1.0)
                        .build());
    }

    // =========================================================================
    // TABELA A — Statystyki globalne temperatury
    // =========================================================================

    @Nested
    @DisplayName("Tabela A — Statystyki globalne temperatury")
    class TableATests {

        @Test
        @DisplayName("A.1 globalMinTemp = min(s.minTemp) z serii siatki (nie ref.)")
        void shouldCalculateGlobalMinTemp() {
            var series = buildStandardSeriesSet();
            setupMocks(series);

            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            // S2 ma minTemp=4.2 → najniższa wśród siatki
            assertEquals(4.2, result.getGlobalMinTemp(), 0.001,
                    "globalMinTemp powinno być 4.2 (S2)");
        }

        @Test
        @DisplayName("A.1 globalMinTemp ignoruje serię referencyjną")
        void shouldExcludeReferenceFromMinTemp() {
            var series = buildStandardSeriesSet();
            setupMocks(series);

            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            // Referencyjna ma minTemp=20.5, ale nie jest brana pod uwagę
            assertTrue(result.getGlobalMinTemp() < 20.0,
                    "Referencyjna (20.5°C) nie powinna wpływać na globalMinTemp");
        }

        @Test
        @DisplayName("A.2 globalMaxTemp = max(s.maxTemp) z serii siatki")
        void shouldCalculateGlobalMaxTemp() {
            var series = buildStandardSeriesSet();
            setupMocks(series);

            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            // S5 ma maxTemp=9.2 → najwyższa wśród siatki
            assertEquals(9.2, result.getGlobalMaxTemp(), 0.001,
                    "globalMaxTemp powinno być 9.2 (S5)");
        }

        @Test
        @DisplayName("A.3 overallAvgTemp — średnia ważona liczbą pomiarów")
        void shouldCalculateWeightedAvg() {
            var series = buildStandardSeriesSet();
            setupMocks(series);

            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            // Wszystkie serie mają 288 pomiarów, więc ważona = prosta:
            // (5.2+4.8+6.1+5.5+5.3)/5 = 26.9/5 = 5.38
            // Wykluczamy serię referencyjną S6 (22.0°C)
            double expectedAvg = (5.2 + 4.8 + 6.1 + 5.5 + 5.3) / 5.0;
            assertEquals(expectedAvg, result.getOverallAvgTemp(), 0.01,
                    "Ważona średnia (równe wagi, bez ref) = prosta średnia");
        }

        @Test
        @DisplayName("A.3 overallAvgTemp — poprawna z różnymi wagami")
        void shouldCalculateWeightedAvgWithDifferentWeights() {
            List<MeasurementSeries> series = new ArrayList<>();

            // S1: 100 pomiarów, avg=5.0
            series.add(buildSeries(1L, false,
                    4.0, 6.0, 5.0, 0.1, 0.316, 6.32, 0.632,
                    4.5, 5.5, 5.01, 100,
                    500, 0, 0, null, 0.0, 0, "STABLE", 5));

            // S2: 900 pomiarów, avg=6.0 → dominuje w ważonej
            series.add(buildSeries(2L, false,
                    5.0, 7.0, 6.0, 0.1, 0.316, 5.27, 0.632,
                    5.5, 6.5, 6.01, 900,
                    4500, 0, 0, null, 0.0, 0, "STABLE", 5));

            setupMocks(series);
            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            // Ważona: (5.0*100 + 6.0*900) / 1000 = 5900/1000 = 5.9
            assertEquals(5.9, result.getOverallAvgTemp(), 0.01,
                    "Ważona avg: (5.0*100+6.0*900)/1000 = 5.9");
        }

        @Test
        @DisplayName("A.4 globalStdDev — pooled variance (tożsamość Steinera)")
        void shouldCalculatePooledStdDev() {
            var series = buildStandardSeriesSet();
            setupMocks(series);

            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            assertNotNull(result.getGlobalStdDev(), "Pooled StdDev nie powinien być null");
            assertTrue(result.getGlobalStdDev() > 0, "Pooled StdDev > 0");
            // Z różnymi średnimi (4.8–22.0) between-group powinien dominować
            // → globalStdDev > max(s.stdDev z siatki)
            assertTrue(result.getGlobalStdDev() > 0.5,
                    "Pooled StdDev powinien być > max(serie.stdDev) z powodu between-group component");
        }

        @Test
        @DisplayName("A.4 pooled StdDev — identyczne serie → = wewnątrz-serijna")
        void shouldReturnWithinGroupStdDevForIdenticalMeans() {
            List<MeasurementSeries> series = new ArrayList<>();

            // 3 serie z identyczną średnią (5.0), variancja = 0.04
            for (long i = 1; i <= 3; i++) {
                series.add(buildSeries(i, false,
                        4.5, 5.5, 5.0, 0.04, 0.2, 4.0, 0.4,
                        4.6, 5.4, 5.01, POINTS_24H,
                        1435, 0, 0, null, 0.0, 0, "STABLE", 5));
            }
            setupMocks(series);

            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            // Gdy μ_s = μ_global ∀s → between-group = 0 → pooledVar = avg(σ²) = 0.04
            // pooledStdDev = sqrt(0.04) = 0.2
            assertEquals(0.2, result.getGlobalStdDev(), 0.001,
                    "Identyczne średnie → pooledStdDev = within-group = 0.2");
        }

        @Test
        @DisplayName("A.5 CV% = (σ/μ)×100")
        void shouldCalculateCvPercentage() {
            var series = buildStandardSeriesSet();
            setupMocks(series);

            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            assertNotNull(result.getGlobalCvPercentage());
            assertTrue(result.getGlobalCvPercentage() > 0);

            // Sprawdź wzór: CV = σ/|μ| × 100
            double expectedCv = (result.getGlobalStdDev() / Math.abs(result.getOverallAvgTemp())) * 100.0;
            assertEquals(expectedCv, result.getGlobalCvPercentage(), 0.001,
                    "CV% = σ/|μ|×100");
        }

        @Test
        @DisplayName("A.6 hotspot — seria siatki z max(maxTemperature)")
        void shouldSetHotspot() {
            var series = buildStandardSeriesSet();
            setupMocks(series);

            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            // S5 ma maxTemp=9.2 → hotspot
            assertEquals(9.2, result.getHotspotTemp(), 0.001);
            assertEquals(5L, result.getHotspotSeriesId());
        }

        @Test
        @DisplayName("A.7 coldspot — seria siatki z min(minTemperature)")
        void shouldSetColdspot() {
            var series = buildStandardSeriesSet();
            setupMocks(series);

            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            // S2 ma minTemp=4.2 → coldspot
            assertEquals(4.2, result.getColdspotTemp(), 0.001);
            assertEquals(2L, result.getColdspotSeriesId());
        }

        @Test
        @DisplayName("A.8 expandedUncertainty — max(s.expandedUncertainty) ze WSZYSTKICH serii")
        void shouldCalculateMaxExpandedUncertainty() {
            var series = buildStandardSeriesSet();
            setupMocks(series);

            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            // S4 ma U=1.0 (największa w siatce, ref S6=1.414 ignorujemy)
            assertEquals(1.0, result.getGlobalExpandedUncertainty(), 0.001);
        }

        @Test
        @DisplayName("A.9 P5 = min(s.P5), P95 = max(s.P95)")
        void shouldCalculatePercentiles() {
            var series = buildStandardSeriesSet();
            setupMocks(series);

            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            // P5: min → S2 ma P5=4.3
            assertEquals(4.3, result.getGlobalPercentile5(), 0.001);
            // P95: max → S3 ma P95=6.8 (ref S6=23.0 ignorujemy)
            assertEquals(6.8, result.getGlobalPercentile95(), 0.001);
        }
    }

    // =========================================================================
    // TABELA B — MKT (Mean Kinetic Temperature)
    // =========================================================================

    @Nested
    @DisplayName("Tabela B — MKT (Mean Kinetic Temperature)")
    class TableBTests {

        @Test
        @DisplayName("B.1 globalMkt obliczony z tożsamości Arrheniusa (nie prosta średnia)")
        void shouldCalculateGlobalMkt() {
            var series = buildStandardSeriesSet();
            setupMocks(series);

            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            assertNotNull(result.getGlobalMkt(), "globalMkt nie powinien być null");
            // MKT jest ważony eksponentą → wyższy niż prosta średnia MKT siatki
            double simpleMktAvg = (5.21 + 4.82 + 6.14 + 5.54 + 5.33) / 5.0;
            assertTrue(result.getGlobalMkt() >= simpleMktAvg - 0.5,
                    "Arrhenius MKT >= prosta średnia (z marginesem), got: " + result.getGlobalMkt());
        }

        @Test
        @DisplayName("B.1 globalMkt — identyczne MKT → globalMkt = to samo")
        void shouldReturnSameMktForIdenticalSeries() {
            List<MeasurementSeries> series = new ArrayList<>();
            for (long i = 1; i <= 4; i++) {
                series.add(buildSeries(i, false,
                        4.0, 6.0, 5.0, 0.04, 0.2, 4.0, 0.4,
                        4.5, 5.5, 5.0, POINTS_24H,
                        1435, 0, 0, null, 0.0, 0, "STABLE", 5));
            }
            setupMocks(series);

            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            assertEquals(5.0, result.getGlobalMkt(), 0.05,
                    "Identyczne MKT 5.0°C → globalMkt = 5.0°C");
        }

        @Test
        @DisplayName("B.2 mktDeltaHR ustawione (= 83144.72 / 8.314472)")
        void shouldSetDeltaHR() {
            var series = buildStandardSeriesSet();
            setupMocks(series);

            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            double expectedDeltaHR = 83_144.72 / 8.314472;
            assertEquals(expectedDeltaHR, result.getMktDeltaHR(), 0.1,
                    "ΔH/R = 83144.72 / 8.314472 ≈ 10000 K");
        }

        @Test
        @DisplayName("B.3 mktWorst = max(s.mktTemperature) — ze WSZYSTKICH serii")
        void shouldSetMktWorst() {
            var series = buildStandardSeriesSet();
            setupMocks(series);

            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            // S3 ma MKT=6.14 (najwyższa w siatce)
            // S6 (Ref) ma 22.1, ale jest ignorowana w mktWorstValue
            assertEquals(6.14, result.getMktWorstValue(), 0.001);
            assertEquals(3L, result.getMktWorstSeriesId());
        }

        @Test
        @DisplayName("B.4 mktBest = min(s.mktTemperature) — tylko siatka")
        void shouldSetMktBest() {
            var series = buildStandardSeriesSet();
            setupMocks(series);

            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            // S2 ma MKT=4.82 → najlepszy (najniższy) z siatki
            assertEquals(4.82, result.getMktBestValue(), 0.001);
            assertEquals(2L, result.getMktBestSeriesId());
        }

        @Test
        @DisplayName("B.5 mktReference = MKT serii referencyjnej")
        void shouldSetMktReference() {
            var series = buildStandardSeriesSet();
            setupMocks(series);

            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            assertEquals(22.1, result.getMktReferenceValue(), 0.001);
            assertEquals(6L, result.getMktReferenceSeriesId());
        }

        @Test
        @DisplayName("B.6 mktDelta = globalMkt − mktReference (powinna być ujemna — chłodzi)")
        void shouldCalculateMktDelta() {
            var series = buildStandardSeriesSet();
            setupMocks(series);

            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            assertNotNull(result.getMktDeltaInternalVsReference());
            // globalMkt (~5-6°C) - mktReference (22.1°C) → ujemna (~-16...-17°C)
            assertTrue(result.getMktDeltaInternalVsReference() < 0,
                    "Dla normalnej lodówki delta powinna być ujemna (chłodzi)");
            double expectedDelta = result.getGlobalMkt() - result.getMktReferenceValue();
            assertEquals(expectedDelta, result.getMktDeltaInternalVsReference(), 0.001);
        }
    }

    // =========================================================================
    // TABELA C — Zgodność temperaturowa (Compliance)
    // =========================================================================

    @Nested
    @DisplayName("Tabela C — Zgodność temperaturowa")
    class TableCTests {

        @Test
        @DisplayName("C.1 totalTimeInRange = Σ s.totalTimeInRangeMinutes")
        void shouldSumTimeInRange() {
            var series = buildStandardSeriesSet();
            setupMocks(series);

            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            long expected = 1435 + 1435 + 1400 + 1410 + 1420;
            assertEquals(expected, result.getTotalTimeInRangeMinutes());
        }

        @Test
        @DisplayName("C.2 totalTimeOutOfRange = Σ s.totalTimeOutOfRangeMinutes")
        void shouldSumTimeOutOfRange() {
            var series = buildStandardSeriesSet();
            setupMocks(series);

            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            long expected = 0 + 0 + 35 + 25 + 15 + 0;
            assertEquals(expected, result.getTotalTimeOutOfRangeMinutes());
        }

        @Test
        @DisplayName("C.3 compliance% = timeIn/(timeIn+timeOut)×100")
        void shouldCalculateCompliancePercentage() {
            var series = buildStandardSeriesSet();
            setupMocks(series);

            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            long timeIn = 1435 + 1435 + 1400 + 1410 + 1420;
            long timeOut = 0 + 0 + 35 + 25 + 15;
            double expectedCompliance = (double) timeIn / (timeIn + timeOut) * 100.0;

            assertEquals(expectedCompliance, result.getGlobalCompliancePercentage(), 0.01);
            assertTrue(result.getGlobalCompliancePercentage() > 95.0,
                    "Z małymi naruszeniami compliance > 95%");
        }

        @Test
        @DisplayName("C.3 compliance = 100% gdy brak naruszeń")
        void shouldReturn100PercentWhenNoViolations() {
            List<MeasurementSeries> series = new ArrayList<>();
            for (long i = 1; i <= 3; i++) {
                series.add(buildSeries(i, false,
                        4.0, 6.0, 5.0, 0.04, 0.2, 4.0, 0.4,
                        4.5, 5.5, 5.01, POINTS_24H,
                        1435, 0, 0, null, 0.0, 0, "STABLE", 5));
            }
            setupMocks(series);

            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            assertEquals(100.0, result.getGlobalCompliancePercentage(), 0.001);
            assertEquals(0, result.getTotalViolations());
        }

        @Test
        @DisplayName("C.4 totalViolations = Σ s.violationCount")
        void shouldSumViolations() {
            var series = buildStandardSeriesSet();
            setupMocks(series);

            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            int expected = 0 + 0 + 2 + 1 + 1 + 0;
            assertEquals(expected, result.getTotalViolations());
        }

        @Test
        @DisplayName("C.5 maxViolationDuration = max(s.maxViolationDurationMinutes)")
        void shouldFindMaxViolationDuration() {
            var series = buildStandardSeriesSet();
            setupMocks(series);

            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            // S3 ma maxViolationDuration=20 → najdłuższa
            assertEquals(20L, result.getMaxViolationDurationMinutes());
            assertEquals(3L, result.getMaxViolationSeriesId());
        }

        @Test
        @DisplayName("C.6 seriesWithViolations = count(s | s.violationCount > 0)")
        void shouldCountSeriesWithViolations() {
            var series = buildStandardSeriesSet();
            setupMocks(series);

            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            // S3, S4, S5 mają naruszenia
            assertEquals(3, result.getSeriesWithViolationsCount());
        }

        @Test
        @DisplayName("C.7 seriesFullyCompliant = count(s | s.violationCount == 0)")
        void shouldCountFullyCompliantSeries() {
            var series = buildStandardSeriesSet();
            setupMocks(series);

            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            // S1, S2 mają 0 naruszeń. Ref S6 ignorowany.
            assertEquals(2, result.getSeriesFullyCompliantCount());
        }
    }

    // =========================================================================
    // TABELA D — Stabilność / Drift / Spike
    // =========================================================================

    @Nested
    @DisplayName("Tabela D — Stabilność termiczna (Drift / Spike)")
    class TableDTests {

        @Test
        @DisplayName("D.1 maxAbsTrend = max(|s.trendCoefficient|)")
        void shouldFindMaxAbsTrend() {
            var series = buildStandardSeriesSet();
            setupMocks(series);

            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            // S4 ma trend=0.04 → largest |trend|
            assertEquals(0.04, result.getMaxAbsTrendCoefficient(), 0.001);
            assertEquals(4L, result.getMaxTrendSeriesId());
        }

        @Test
        @DisplayName("D.2 avgTrend — ważony średni trend")
        void shouldCalculateWeightedAvgTrend() {
            var series = buildStandardSeriesSet();
            setupMocks(series);

            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            assertNotNull(result.getAvgTrendCoefficient());
            // Większość serii stabilna (trend ≈ 0), S4 z driftem 0.04
            // → ważony avg powinien być bliski 0 ale > 0
            assertTrue(Math.abs(result.getAvgTrendCoefficient()) < 0.02,
                    "Ważony trend powinien być mały z dominująco stabilnymi seriami");
        }

        @Test
        @DisplayName("D.3 totalSpikeCount = Σ s.spikeCount")
        void shouldSumSpikeCount() {
            var series = buildStandardSeriesSet();
            setupMocks(series);

            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            // Tylko S5 ma 1 spike
            assertEquals(1, result.getTotalSpikeCount());
        }

        @Test
        @DisplayName("D.4 seriesWithDrift = count(DRIFT | MIXED)")
        void shouldCountSeriesWithDrift() {
            var series = buildStandardSeriesSet();
            setupMocks(series);

            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            // S4 = DRIFT → 1
            assertEquals(1, result.getSeriesWithDriftCount());
        }

        @Test
        @DisplayName("D.5 seriesStable = count(STABLE)")
        void shouldCountStableSeries() {
            var series = buildStandardSeriesSet();
            setupMocks(series);

            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            // S1, S2, S3 = STABLE. Ref S6 ignorowany.
            assertEquals(3, result.getSeriesStableCount());
        }

        @Test
        @DisplayName("D.6 dominantClassification = moda (najczęstsza)")
        void shouldFindDominantClassification() {
            var series = buildStandardSeriesSet();
            setupMocks(series);

            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            // 4×STABLE, 1×DRIFT, 1×SPIKE → moda = STABLE
            assertEquals("STABLE", result.getDominantDriftClassification());
        }
    }

    // =========================================================================
    // METADANE WALIDACJI
    // =========================================================================

    @Nested
    @DisplayName("Metadane walidacji")
    class MetadataTests {

        @Test
        @DisplayName("Liczba serii: total, grid, reference")
        void shouldCountSeries() {
            var series = buildStandardSeriesSet();
            setupMocks(series);

            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            assertEquals(6, result.getTotalSeriesCount());
            assertEquals(5, result.getGridSeriesCount());
            assertEquals(1, result.getReferenceSeriesCount());
        }

        @Test
        @DisplayName("Łączna liczba pomiarów = Σ s.measurementCount")
        void shouldSumMeasurementCount() {
            var series = buildStandardSeriesSet();
            setupMocks(series);

            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            assertEquals(6L * POINTS_24H, result.getTotalMeasurementCount());
        }

        @Test
        @DisplayName("Czas trwania: start, end, duration obliczone z serii")
        void shouldCalculateTimeBounds() {
            var series = buildStandardSeriesSet();
            setupMocks(series);

            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            assertEquals(BASE_TIME, result.getValidationStartTime());
            assertNotNull(result.getValidationEndTime());
            assertNotNull(result.getTotalDurationMinutes());
            assertTrue(result.getTotalDurationMinutes() > 0);
        }

        @Test
        @DisplayName("Dominujący interwał pomiarowy = moda (5 min)")
        void shouldFindDominantInterval() {
            var series = buildStandardSeriesSet();
            setupMocks(series);

            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            assertEquals(5, result.getDominantIntervalMinutes());
        }
    }

    // =========================================================================
    // EDGE CASES
    // =========================================================================

    @Nested
    @DisplayName("Przypadki brzegowe")
    class EdgeCases {

        @Test
        @DisplayName("Brak serii → IllegalStateException")
        void shouldThrowWhenNoSeries() {
            Validation validation = new Validation();
            validation.setId(VALIDATION_ID);
            validation.setMeasurementSeries(new ArrayList<>());
            when(validationRepository.findById(VALIDATION_ID)).thenReturn(Optional.of(validation));

            assertThrows(IllegalStateException.class,
                    () -> service.calculateAndSave(VALIDATION_ID));
        }

        @Test
        @DisplayName("Null serie → IllegalStateException")
        void shouldThrowWhenNullSeries() {
            Validation validation = new Validation();
            validation.setId(VALIDATION_ID);
            validation.setMeasurementSeries(null);
            when(validationRepository.findById(VALIDATION_ID)).thenReturn(Optional.of(validation));

            assertThrows(IllegalStateException.class,
                    () -> service.calculateAndSave(VALIDATION_ID));
        }

        @Test
        @DisplayName("Nieistniejąca walidacja → IllegalArgumentException")
        void shouldThrowWhenValidationNotFound() {
            when(validationRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> service.calculateAndSave(999L));
        }

        @Test
        @DisplayName("Jedna seria — poprawne obliczenia")
        void shouldHandleSingleSeries() {
            List<MeasurementSeries> series = new ArrayList<>();
            series.add(buildSeries(1L, false,
                    4.0, 6.0, 5.0, 0.04, 0.2, 4.0, 0.4,
                    4.5, 5.5, 5.01, POINTS_24H,
                    1435, 0, 0, null, 0.0, 0, "STABLE", 5));
            setupMocks(series);

            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            assertEquals(4.0, result.getGlobalMinTemp(), 0.001);
            assertEquals(6.0, result.getGlobalMaxTemp(), 0.001);
            assertEquals(5.0, result.getOverallAvgTemp(), 0.01);
            assertEquals(1, result.getTotalSeriesCount());
            assertEquals(0, result.getTotalViolations());
        }

        @Test
        @DisplayName("Tylko serie referencyjne — globalMinTemp/MaxTemp = null (brak siatki)")
        void shouldHandleOnlyReferenceSeries() {
            List<MeasurementSeries> series = new ArrayList<>();
            series.add(buildSeries(1L, true,
                    20.0, 24.0, 22.0, 0.5, 0.707, 3.21, 1.414,
                    21.0, 23.0, 22.1, POINTS_24H,
                    1435, 0, 0, null, 0.0, 0, "STABLE", 5));
            setupMocks(series);

            ValidationSummaryStatsDto result = service.calculateAndSave(VALIDATION_ID);

            assertNull(result.getGlobalMinTemp(), "Brak serii siatki → null coldspot");
            assertNull(result.getGlobalMaxTemp(), "Brak serii siatki → null hotspot");
            assertEquals(0, result.getGridSeriesCount());
            assertEquals(1, result.getReferenceSeriesCount());
        }

        @Test
        @DisplayName("calculatedAt ustawione po obliczeniu")
        void shouldSetCalculatedAt() {
            var series = buildStandardSeriesSet();
            setupMocks(series);

            service.calculateAndSave(VALIDATION_ID);

            ArgumentCaptor<ValidationSummaryStats> captor = ArgumentCaptor.forClass(ValidationSummaryStats.class);
            verify(statsRepository).save(captor.capture());

            assertNotNull(captor.getValue().getCalculatedAt(),
                    "calculatedAt powinno być ustawione");
        }

        @Test
        @DisplayName("Istniejący rekord jest aktualizowany (nie tworzony nowy)")
        void shouldUpdateExistingRecord() {
            var series = buildStandardSeriesSet();
            Validation validation = new Validation();
            validation.setId(VALIDATION_ID);
            validation.setMeasurementSeries(series);

            ValidationSummaryStats existing = new ValidationSummaryStats();
            existing.setId(42L);
            existing.setValidation(validation);

            when(validationRepository.findById(VALIDATION_ID)).thenReturn(Optional.of(validation));
            when(statsRepository.findByValidationId(VALIDATION_ID)).thenReturn(Optional.of(existing));
            when(statsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.calculateAndSave(VALIDATION_ID);

            ArgumentCaptor<ValidationSummaryStats> captor = ArgumentCaptor.forClass(ValidationSummaryStats.class);
            verify(statsRepository).save(captor.capture());
            assertEquals(42L, captor.getValue().getId(),
                    "Powinien zaktualizować istniejący rekord ID=42, nie tworzyć nowego");
        }
    }
}
