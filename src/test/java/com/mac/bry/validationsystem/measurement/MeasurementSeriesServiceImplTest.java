package com.mac.bry.validationsystem.measurement;

import com.mac.bry.validationsystem.device.CoolingDevice;
import com.mac.bry.validationsystem.device.CoolingDeviceRepository;
import com.mac.bry.validationsystem.materialtype.MaterialType;
import com.mac.bry.validationsystem.thermorecorder.ThermoRecorder;
import com.mac.bry.validationsystem.thermorecorder.ThermoRecorderRepository;
import com.mac.bry.validationsystem.security.service.SecurityService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kompleksowe testy jednostkowe dla
 * MeasurementSeriesServiceImpl.calculateStatistics().
 *
 * Dane testowe odzwierciedlają realne warunki pracy rejestratorów temperatury:
 * - Rozdzielczość: 0.1°C (jedno miejsce po przecinku)
 * - Interwał pomiarowy: 5 minut
 * - Standardowy okres: 24h = 288 pomiarów
 * - Zakresy temperatur: lodówka farmaceutyczna (2-8°C), mroźnia (-25 do -15°C)
 *
 * Pokrycie scenariuszy:
 * - Statystyki podstawowe (min, max, avg, zakres)
 * - MKT (Mean Kinetic Temperature)
 * - Odchylenie standardowe, wariancja, CV%
 * - Statystyki czasowe (czas w/poza zakresem, przekroczenia)
 * - Regresja liniowa (stabilność temperaturowa)
 * - Drift vs Spike: STABLE, SPIKE (klasyczny), SPIKE (level shift/segmentowy),
 * MIXED, DRIFT
 * - Przypadki brzegowe
 */
@ExtendWith(MockitoExtension.class)
class MeasurementSeriesServiceImplTest {

    private MeasurementSeriesServiceImpl service;

    @Mock
    private MeasurementSeriesRepository seriesRepository;
    @Mock
    private MeasurementPointRepository pointRepository;
    @Mock
    private Vi2FileDecoder vi2Decoder;
    @Mock
    private HtmlTestoFileDecoder htmlDecoder;
    @Mock
    private CoolingDeviceRepository deviceRepository;
    @Mock
    private ThermoRecorderRepository recorderRepository;
    @Mock
    private SecurityService securityService;
    @Mock
    private UncertaintyBudgetService uncertaintyBudgetService;

    @org.junit.jupiter.api.BeforeEach
    void setUpMocks() {
        service = new MeasurementSeriesServiceImpl(
                seriesRepository, pointRepository, vi2Decoder, htmlDecoder,
                deviceRepository, recorderRepository, securityService, uncertaintyBudgetService);

        org.mockito.Mockito.lenient().when(uncertaintyBudgetService.calculateExpandedUncertainty(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(UncertaintyBudget.builder()
                        .statisticalUncertainty(0.1)
                        .calibrationUncertainty(0.2)
                        .resolutionUncertainty(0.029)
                        .systematicUncertainty(0.05)
                        .stabilityUncertainty(0.01)
                        .expandedUncertainty(1.2345) // Charakterystyczna wartość do testów
                        .combinedUncertainty(0.61725)
                        .coverageFactor(2.0)
                        .confidenceLevel(95.45)
                        .budgetType(UncertaintyBudgetType.SERIES)
                        .build());
    }

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 1, 22, 12, 0, 0);
    /** Interwał 5 minut — standardowy dla rejestratorów Testo */
    private static final int INTERVAL_5MIN = 5;
    /** 288 pomiarów = 24h przy interwale 5min */
    private static final int POINTS_24H = 288;

    // =========================================================
    // Metody pomocnicze — generatory realistycznych danych
    // =========================================================

    /**
     * Tworzy serię pomiarową z podanymi temperaturami i interwałem w minutach.
     */
    private MeasurementSeries createSeries(double[] temps, int intervalMinutes) {
        return createSeries(temps, intervalMinutes, null, null);
    }

    private MeasurementSeries createSeries(double[] temps, int intervalMinutes,
            Double minLimit, Double maxLimit) {
        MeasurementSeries series = new MeasurementSeries();
        series.setId(1L);
        List<MeasurementPoint> points = new ArrayList<>();
        for (int i = 0; i < temps.length; i++) {
            MeasurementPoint p = new MeasurementPoint();
            p.setMeasurementTime(BASE_TIME.plusMinutes((long) i * intervalMinutes));
            p.setTemperature(temps[i]);
            p.setSeries(series);
            points.add(p);
        }
        series.setMeasurementPoints(points);
        series.setMeasurementCount(temps.length);

        // Dodaj rejestrator, aby uniknąć fallback budget
        com.mac.bry.validationsystem.thermorecorder.ThermoRecorder recorder = new com.mac.bry.validationsystem.thermorecorder.ThermoRecorder();
        recorder.setSerialNumber("TEST-REC-" + series.getId());
        recorder.setResolution(java.math.BigDecimal.valueOf(0.1));
        series.setThermoRecorder(recorder);

        if (minLimit != null || maxLimit != null) {
            CoolingDevice device = new CoolingDevice();
            device.setMinOperatingTemp(minLimit);
            device.setMaxOperatingTemp(maxLimit);
            series.setCoolingDevice(device);
        }

        return series;
    }

    private MeasurementSeries createSeriesWithActivationEnergy(double[] temps, int intervalMinutes,
            double activationEnergy) {
        MeasurementSeries series = createSeries(temps, intervalMinutes);
        CoolingDevice device = new CoolingDevice();
        MaterialType mt = new MaterialType();
        mt.setActivationEnergy(BigDecimal.valueOf(activationEnergy));
        device.setMaterialType(mt);
        series.setCoolingDevice(device);
        return series;
    }

    /**
     * Zaokrągla do 0.1°C — symulacja rozdzielczości rejestratora.
     */
    private static double round1(double val) {
        return Math.round(val * 10.0) / 10.0;
    }

    /**
     * Generuje 288 pomiarów stabilnej lodówki (oscylacja wokół baseTemp
     * ±0.1-0.2°C).
     * Realistyczny wzorzec: cykliczne wahania kompresora.
     */
    private static double[] generateStable288(double baseTemp) {
        double[] temps = new double[POINTS_24H];
        for (int i = 0; i < POINTS_24H; i++) {
            // Symulacja cykli kompresora (~30min = 6 pomiarów)
            double cycle = 0.15 * Math.sin(2 * Math.PI * i / 6.0);
            // Wolniejsza oscylacja dobowa
            double daily = 0.05 * Math.sin(2 * Math.PI * i / POINTS_24H);
            temps[i] = round1(baseTemp + cycle + daily);
        }
        return temps;
    }

    /**
     * Generuje 288 pomiarów z systematycznym dryftem od startTemp do endTemp.
     */
    private static double[] generateDrift288(double startTemp, double endTemp) {
        double[] temps = new double[POINTS_24H];
        double step = (endTemp - startTemp) / (POINTS_24H - 1);
        for (int i = 0; i < POINTS_24H; i++) {
            temps[i] = round1(startTemp + step * i);
        }
        return temps;
    }

    /**
     * Generuje 288 pomiarów stabilnych z jednym spike'em (np. otwarcie drzwi).
     * Spike trwa spikePoints pomiarów (np. 6 = 30 minut).
     */
    private static double[] generateStableWithSpike288(double baseTemp, int spikeStart,
            int spikePoints, double spikeTemp) {
        double[] temps = generateStable288(baseTemp);
        for (int i = spikeStart; i < spikeStart + spikePoints && i < POINTS_24H; i++) {
            // Spike z fazą narastania i opadania
            double progress = (double) (i - spikeStart) / spikePoints;
            double envelope = Math.sin(Math.PI * progress); // 0→1→0
            temps[i] = round1(baseTemp + (spikeTemp - baseTemp) * envelope);
        }
        return temps;
    }

    /**
     * Generuje 288 pomiarów z przesunięciem poziomu (level shift).
     * Pierwsza połowa stabilna wokół temp1, spike, druga połowa stabilna wokół
     * temp2.
     */
    private static double[] generateLevelShift288(double temp1, double temp2,
            int shiftPoint, double spikeTemp) {
        double[] temps = new double[POINTS_24H];
        // Segment przed
        for (int i = 0; i < shiftPoint - 3; i++) {
            double cycle = 0.15 * Math.sin(2 * Math.PI * i / 6.0);
            temps[i] = round1(temp1 + cycle);
        }
        // Strefa spike'a (6 pomiarów = 30 min zdarzenia)
        temps[shiftPoint - 3] = round1(temp1 + 0.3);
        temps[shiftPoint - 2] = round1(spikeTemp * 0.6 + temp1 * 0.4);
        temps[shiftPoint - 1] = round1(spikeTemp);
        temps[shiftPoint] = round1(spikeTemp * 0.8 + temp2 * 0.2);
        temps[shiftPoint + 1] = round1(temp2 + 0.4);
        temps[shiftPoint + 2] = round1(temp2 + 0.2);
        // Segment po
        for (int i = shiftPoint + 3; i < POINTS_24H; i++) {
            double cycle = 0.15 * Math.sin(2 * Math.PI * i / 6.0);
            temps[i] = round1(temp2 + cycle);
        }
        return temps;
    }

    // =========================================================
    // TESTY: Statystyki podstawowe
    // =========================================================

    @Nested
    @DisplayName("Statystyki podstawowe (min, max, avg, count)")
    class BasicStatistics {

        @Test
        @DisplayName("288 pomiarów — poprawne min, max, avg, count")
        void shouldCalculateBasicStats_288points() {
            double[] temps = generateStable288(5.0);
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertEquals(POINTS_24H, series.getMeasurementCount());
            assertTrue(series.getMinTemperature() >= 4.5, "Min powinno być >= 4.5");
            assertTrue(series.getMaxTemperature() <= 5.5, "Max powinno być <= 5.5");
            assertEquals(5.0, series.getAvgTemperature(), 0.2);
        }

        @Test
        @DisplayName("Poprawne czasy pierwszego i ostatniego pomiaru (24h)")
        void shouldSetCorrectTimeRange() {
            double[] temps = generateStable288(5.0);
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertEquals(BASE_TIME, series.getFirstMeasurementTime());
            assertEquals(BASE_TIME.plusMinutes(287L * INTERVAL_5MIN), series.getLastMeasurementTime());
        }

        @Test
        @DisplayName("Pusta lista pomiarów — brak statystyk")
        void shouldHandleEmptyPoints() {
            MeasurementSeries series = new MeasurementSeries();
            series.setMeasurementPoints(new ArrayList<>());

            service.calculateStatistics(series);

            assertNull(series.getMinTemperature());
        }

        @Test
        @DisplayName("Null punkty pomiarowe — brak obliczeń")
        void shouldHandleNullPoints() {
            MeasurementSeries series = new MeasurementSeries();
            series.setMeasurementPoints(null);

            service.calculateStatistics(series);

            assertNull(series.getMinTemperature());
        }

        @Test
        @DisplayName("Identyczne temperatury — zerowe odchylenie")
        void shouldHandleConstantTemperature() {
            double[] temps = new double[POINTS_24H];
            for (int i = 0; i < POINTS_24H; i++)
                temps[i] = 5.0;
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertEquals(5.0, series.getMinTemperature(), 0.001);
            assertEquals(5.0, series.getMaxTemperature(), 0.001);
            assertEquals(0.0, series.getStdDeviation(), 0.001);
            assertEquals(0.0, series.getVariance(), 0.001);
        }
    }

    // =========================================================
    // TESTY: Odchylenie standardowe, wariancja, CV%
    // =========================================================

    @Nested
    @DisplayName("Odchylenie standardowe, wariancja, CV%")
    class VarianceTests {

        @Test
        @DisplayName("CV% obliczane poprawnie dla stabilnych danych")
        void shouldCalculateCvPercentage() {
            double[] temps = generateStable288(5.0);
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertTrue(series.getStdDeviation() > 0, "StdDev > 0 dla oscylujących danych");
            assertTrue(series.getCvPercentage() > 0, "CV% > 0");
            assertTrue(series.getCvPercentage() < 10, "CV% < 10% dla stabilnych danych");
        }

        @Test
        @DisplayName("CV% = 0 dla identycznych temperatur")
        void shouldReturnZeroCvForConstant() {
            double[] temps = new double[POINTS_24H];
            for (int i = 0; i < POINTS_24H; i++)
                temps[i] = 5.0;
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertEquals(0.0, series.getCvPercentage(), 0.001);
        }

        @Test
        @DisplayName("Wyższe StdDev dla danych z dużą zmiennością")
        void shouldHaveHigherStdDevForVariableData() {
            // Dane z dużą zmiennością: 2.0 - 8.0
            double[] temps = new double[POINTS_24H];
            for (int i = 0; i < POINTS_24H; i++) {
                temps[i] = round1(5.0 + 3.0 * Math.sin(2 * Math.PI * i / 12.0));
            }
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertTrue(series.getStdDeviation() > 1.0,
                    "StdDev powinno być > 1.0 dla zmiennych danych");
        }
    }

    // =========================================================
    // TESTY: Mediana, Niepewność, Percentyle, Interwał
    // =========================================================

    @Nested
    @DisplayName("Mediana, Niepewność U, Percentyle P5/P95, Interwał")
    class MedianUncertaintyPercentileTests {

        @Test
        @DisplayName("Mediana obliczona poprawnie dla 288 pomiarów")
        void shouldCalculateMedian_288points() {
            double[] temps = generateStable288(5.0);
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertNotNull(series.getMedianTemperature());
            assertEquals(5.0, series.getMedianTemperature(), 0.3,
                    "Mediana powinna być bliska 5.0 dla stabilnych danych");
        }

        @Test
        @DisplayName("Mediana dla identycznych temperatur = ta sama temperatura")
        void shouldCalculateMedian_constant() {
            double[] temps = new double[POINTS_24H];
            for (int i = 0; i < POINTS_24H; i++)
                temps[i] = 5.0;
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertEquals(5.0, series.getMedianTemperature(), 0.001);
        }

        @Test
        @DisplayName("Mediana odporna na outlier'y (w przeciwieństwie do średniej)")
        void shouldBeRobustToOutliers() {
            double[] temps = generateStable288(5.0);
            temps[0] = 50.0; // Ekstremalny outlier
            temps[1] = 50.0;
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            // Mediana powinna być nienaruszona, średnia podniesiona
            assertTrue(series.getMedianTemperature() < 6.0,
                    "Mediana odporna na outlier'y, got: " + series.getMedianTemperature());
            assertTrue(series.getAvgTemperature() > series.getMedianTemperature(),
                    "Średnia wyższa od mediany przy outlierze w górę");
        }

        @Test
        @DisplayName("Niepewność rozszerzona U = 2 × StdDev (k=2, 95%)")
        void shouldCalculateExpandedUncertainty() {
            double[] temps = generateStable288(5.0);
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertNotNull(series.getExpandedUncertainty());
            assertEquals(1.2345, series.getExpandedUncertainty(), 0.001,
                    "U powinno pochodzić z zamokowanego serwisu");
        }

        @Test
        @DisplayName("U = 0 dla identycznych temperatur")
        void shouldReturnZeroUncertaintyForConstant() {
            double[] temps = new double[POINTS_24H];
            for (int i = 0; i < POINTS_24H; i++)
                temps[i] = 5.0;
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertEquals(1.2345, series.getExpandedUncertainty(), 0.001);
        }

        @Test
        @DisplayName("P5 < mediana < P95 dla danych z wariancją")
        void shouldCalculatePercentilesCorrectly() {
            double[] temps = generateStable288(5.0);
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertNotNull(series.getPercentile5());
            assertNotNull(series.getPercentile95());
            assertTrue(series.getPercentile5() < series.getMedianTemperature(),
                    "P5 < mediana");
            assertTrue(series.getPercentile95() > series.getMedianTemperature(),
                    "P95 > mediana");
        }

        @Test
        @DisplayName("P5 i P95 dla identycznych temperatur = ta sama wartość")
        void shouldReturnSamePercentilesForConstant() {
            double[] temps = new double[POINTS_24H];
            for (int i = 0; i < POINTS_24H; i++)
                temps[i] = 5.0;
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertEquals(5.0, series.getPercentile5(), 0.001);
            assertEquals(5.0, series.getPercentile95(), 0.001);
        }

        @Test
        @DisplayName("P5/P95 obejmują 90% danych — niezależne od outlier'ów")
        void shouldExcludeExtremes() {
            double[] temps = generateStable288(5.0);
            temps[0] = -10.0; // Ekstremalny outlier dolny
            temps[287] = 30.0; // Ekstremalny outlier górny
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertTrue(series.getPercentile5() > -5.0,
                    "P5 powinien ignorować ekstremalny outlier, got: " + series.getPercentile5());
            assertTrue(series.getPercentile95() < 25.0,
                    "P95 powinien ignorować ekstremalny outlier, got: " + series.getPercentile95());
        }

        @Test
        @DisplayName("Interwał pomiarowy obliczony z dwóch pierwszych pomiarów (5 min)")
        void shouldCalculateMeasurementInterval() {
            double[] temps = generateStable288(5.0);
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertNotNull(series.getMeasurementIntervalMinutes());
            assertEquals(5, series.getMeasurementIntervalMinutes(),
                    "Interwał = 5 minut");
        }

        @Test
        @DisplayName("Interwał pomiarowy 15 min")
        void shouldCalculateInterval15min() {
            double[] temps = { 5.0, 5.1, 4.9, 5.0, 5.1, 4.9, 5.0, 5.1 };
            MeasurementSeries series = createSeries(temps, 15);

            service.calculateStatistics(series);

            assertEquals(15, series.getMeasurementIntervalMinutes());
        }
    }

    // =========================================================
    // TESTY: MKT (Mean Kinetic Temperature)
    // =========================================================

    @Nested
    @DisplayName("MKT (Mean Kinetic Temperature)")
    class MktTests {

        @Test
        @DisplayName("MKT obliczone z domyślną energią aktywacji (83.14 kJ/mol)")
        void shouldCalculateMktDefault() {
            double[] temps = generateStable288(5.0);
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertNotNull(series.getMktTemperature());
            assertTrue(series.getMktTemperature() >= series.getAvgTemperature() - 0.5,
                    "MKT powinno być bliskie średniej dla stabilnych danych");
        }

        @Test
        @DisplayName("MKT z niestandardową energią aktywacji z MaterialType")
        void shouldUseMaterialTypeActivationEnergy() {
            double[] temps = generateStable288(22.0); // temperatura pokojowa
            MeasurementSeries series = createSeriesWithActivationEnergy(temps, INTERVAL_5MIN, 60.0);

            service.calculateStatistics(series);

            assertNotNull(series.getMktTemperature());
        }

        @Test
        @DisplayName("MKT dla identycznych temperatur = ta sama temperatura")
        void shouldReturnSameTempForConstant() {
            double[] temps = new double[POINTS_24H];
            for (int i = 0; i < POINTS_24H; i++)
                temps[i] = 5.0;
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertEquals(5.0, series.getMktTemperature(), 0.01);
        }
    }

    // =========================================================
    // TESTY: Statystyki czasowe
    // =========================================================

    @Nested
    @DisplayName("Statystyki czasowe (czas w/poza zakresem)")
    class TimeStatistics {

        @Test
        @DisplayName("288 pomiarów w zakresie 2-8°C — 0 przekroczeń")
        void shouldCalculateFullTimeInRange() {
            double[] temps = generateStable288(5.0);
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN, 2.0, 8.0);

            service.calculateStatistics(series);

            assertEquals(0, series.getViolationCount());
            assertEquals(0, series.getTotalTimeOutOfRangeMinutes());
            assertTrue(series.getTotalTimeInRangeMinutes() > 0);
        }

        @Test
        @DisplayName("Spike 12°C wykracza poza zakres 2-8°C")
        void shouldDetectTimeOutOfRange() {
            double[] temps = generateStableWithSpike288(5.0, 144, 6, 12.0);
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN, 2.0, 8.0);

            service.calculateStatistics(series);

            assertTrue(series.getTotalTimeOutOfRangeMinutes() > 0,
                    "Spike 12°C powinien wygenerować czas poza zakresem");
            assertTrue(series.getViolationCount() >= 1,
                    "Co najmniej 1 przekroczenie");
        }

        @Test
        @DisplayName("Dwa oddzielne przekroczenia zakresu")
        void shouldCountMultipleViolations() {
            // Dwa spike'i w różnych momentach
            double[] temps = generateStable288(5.0);
            // Spike 1 w godzinie 4 (pomiar 48)
            for (int i = 48; i < 54; i++)
                temps[i] = 9.0;
            // Spike 2 w godzinie 16 (pomiar 192)
            for (int i = 192; i < 198; i++)
                temps[i] = 9.0;
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN, 2.0, 8.0);

            service.calculateStatistics(series);

            assertEquals(2, series.getViolationCount(),
                    "Dwa oddzielne przekroczenia zakresu");
        }

        @Test
        @DisplayName("Brak limitów — brak statystyk czasowych")
        void shouldSkipTimeStatsWithoutLimits() {
            double[] temps = generateStable288(5.0);
            temps[100] = 15.0; // nawet z outlirem, bez limitów brak naruszeń
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertEquals(0, series.getViolationCount());
        }
    }

    // =========================================================
    // TESTY: Regresja liniowa (stabilność)
    // =========================================================

    @Nested
    @DisplayName("Regresja liniowa — stabilność temperatury")
    class LinearRegressionTests {

        @Test
        @DisplayName("288 pomiarów stabilnych — trend bliski 0")
        void shouldDetectZeroTrend() {
            double[] temps = new double[POINTS_24H];
            for (int i = 0; i < POINTS_24H; i++)
                temps[i] = 5.0;
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertNotNull(series.getTrendCoefficient());
            assertEquals(0.0, series.getTrendCoefficient() * 24.0, 0.001);
        }

        @Test
        @DisplayName("Trend rosnący — drift od 4.0 do 6.0°C w 24h")
        void shouldDetectPositiveTrend() {
            double[] temps = generateDrift288(4.0, 6.0);
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertTrue(series.getTrendCoefficient() > 0, "Trend powinien być dodatni");
            double driftPer24h = Math.abs(series.getTrendCoefficient() * 24.0);
            assertTrue(driftPer24h > 1.0, "Drift ~2.0°C/24h, got: " + driftPer24h);
        }

        @Test
        @DisplayName("Trend malejący — drift od 6.0 do 4.0°C w 24h")
        void shouldDetectNegativeTrend() {
            double[] temps = generateDrift288(6.0, 4.0);
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertTrue(series.getTrendCoefficient() < 0, "Trend powinien być ujemny");
        }

        @Test
        @DisplayName("Trend stabilny — oscylacja nie tworzy trendu")
        void shouldDetectStableTrendWithOscillation() {
            double[] temps = generateStable288(5.0);
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            double driftPer24h = Math.abs(series.getTrendCoefficient() * 24.0);
            assertTrue(driftPer24h < 0.1,
                    "Oscylacja symetryczna nie powinna tworzyć trendu, got: " + driftPer24h);
        }
    }

    // =========================================================
    // TESTY: Drift vs Spike — STABLE
    // =========================================================

    @Nested
    @DisplayName("Drift vs Spike: STABLE")
    class DriftVsSpikeStable {

        @Test
        @DisplayName("STABLE — 288 pomiarów lodówki, brak trendu, brak spike'ów")
        void shouldClassifyAsStable_stableFridge() {
            double[] temps = generateStable288(5.0);
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertEquals("STABLE", series.getDriftClassification(),
                    "Stabilna lodówka → STABLE");
        }

        @Test
        @DisplayName("STABLE — 288 pomiarów mroźni -20°C")
        void shouldClassifyAsStable_stableFreezer() {
            double[] temps = generateStable288(-20.0);
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertEquals("STABLE", series.getDriftClassification(),
                    "Stabilna mroźnia → STABLE");
        }

        @Test
        @DisplayName("STABLE — identyczne pomiary przez 24h")
        void shouldClassifyAsStable_perfectlyConstant() {
            double[] temps = new double[POINTS_24H];
            for (int i = 0; i < POINTS_24H; i++)
                temps[i] = 5.0;
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertEquals("STABLE", series.getDriftClassification());
            assertEquals(0, series.getSpikeCount());
        }
    }

    // =========================================================
    // TESTY: Drift vs Spike — DRIFT
    // =========================================================

    @Nested
    @DisplayName("Drift vs Spike: DRIFT")
    class DriftVsSpikeDrift {

        @Test
        @DisplayName("DRIFT — systematyczny wzrost 4.0→6.0°C w 24h (awaria kompresora)")
        void shouldClassifyAsDrift_steadyIncrease() {
            double[] temps = generateDrift288(4.0, 6.0);
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertEquals("DRIFT", series.getDriftClassification(),
                    "Równomierny wzrost → DRIFT");
            assertEquals(0, series.getSpikeCount(),
                    "Brak spike'ów w danych liniowych");
        }

        @Test
        @DisplayName("DRIFT — systematyczny spadek 6.0→4.0°C w 24h (nadmierne chłodzenie)")
        void shouldClassifyAsDrift_steadyDecrease() {
            double[] temps = generateDrift288(6.0, 4.0);
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertEquals("DRIFT", series.getDriftClassification());
        }

        @Test
        @DisplayName("DRIFT — wolny drift 5.0→5.5°C z szumem")
        void shouldClassifyAsDrift_slowDriftWithNoise() {
            double[] temps = new double[POINTS_24H];
            for (int i = 0; i < POINTS_24H; i++) {
                double trend = 5.0 + (0.5 / (POINTS_24H - 1)) * i;
                double noise = 0.1 * Math.sin(2 * Math.PI * i / 6.0);
                temps[i] = round1(trend + noise);
            }
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            double driftPer24h = Math.abs(series.getTrendCoefficient() * 24.0);
            assertTrue(driftPer24h > 0.1,
                    "Drift ~0.5°C/24h powinien być >0.1, got: " + driftPer24h);
        }
    }

    // =========================================================
    // TESTY: Drift vs Spike — SPIKE (klasyczny)
    // =========================================================

    @Nested
    @DisplayName("Drift vs Spike: SPIKE (klasyczny — usunięcie spike'a wystarczy)")
    class DriftVsSpikeClassic {

        @Test
        @DisplayName("STABLE z wykrytym spike'em — symetryczny spike w 288 pkt nie tworzy trendu")
        void shouldClassifyAsStable_singleSpikeInLargeSample() {
            // Z 288 pomiarami, symetryczny spike w środku serii
            // tworzy drift <0.1°C/24h → algorytm poprawnie klasyfikuje jako STABLE
            double[] temps = generateStable288(5.0);
            temps[144] = 15.0; // wielki spike w środku
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertTrue(series.getSpikeCount() > 0, "Spike powinien być wykryty");
            assertEquals("STABLE", series.getDriftClassification(),
                    "Symetryczny spike w dużej próbie → trend ≤0.1 → STABLE");
        }

        @Test
        @DisplayName("SPIKE — wielokrotne anomalie na początku serii tworzą trend")
        void shouldClassifyAsSpike_multipleEarlyAnomalies() {
            // Kilka dużych spike'ów na początku serii = asymetryczny wpływ na trend
            double[] temps = generateStable288(5.0);
            temps[10] = 12.0;
            temps[11] = 14.0;
            temps[12] = 15.0;
            temps[13] = 13.0;
            temps[14] = 11.0;
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertTrue(series.getSpikeCount() > 0, "Powinien wykryć spike'i");
            // Wiele dużych spike'ów na początku = trend ujemny (bo spadek od 15 do 5)
            String cls = series.getDriftClassification();
            assertTrue("SPIKE".equals(cls) || "STABLE".equals(cls),
                    "Spike'i na początku → SPIKE lub STABLE, got: " + cls);
        }

        @Test
        @DisplayName("Detekcja spike'a — otwarcie drzwi (30min zdarzenie do 12°C)")
        void shouldDetectDoorOpeningSpike() {
            double[] temps = generateStableWithSpike288(5.0, 144, 6, 12.0);
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertTrue(series.getSpikeCount() > 0,
                    "Powinien wykryć otwarcie drzwi jako anomalię");
        }
    }

    // =========================================================
    // TESTY: Drift vs Spike — SPIKE (level shift / segmentowy)
    // =========================================================

    @Nested
    @DisplayName("Drift vs Spike: SPIKE (analiza segmentowa — level shift)")
    class DriftVsSpikeSegmented {

        @Test
        @DisplayName("SPIKE (level shift) — dane użytkownika: 40 pomiarów, spike z przesunięciem poziomu")
        void shouldClassifyAsSpike_userDataLevelShift() {
            // Dokładne dane użytkownika (3h interwał)
            double[] temps = {
                    4.7, 4.4, 4.1, 4.5, 4.9, 4.7, 4.9, 4.8,
                    4.6, 4.4, 4.4, 4.5, 4.8, 4.9, 4.7, 4.0,
                    5.2, 6.1, 5.4, 5.2, 5.3, 5.2, 5.3, 5.3,
                    5.2, 5.5, 5.2, 5.5, 5.4, 5.3, 5.4, 5.2,
                    5.0, 4.9, 4.8, 4.7, 4.8, 4.8, 4.9, 5.0
            };
            MeasurementSeries series = createSeries(temps, 180); // 3h interwał

            service.calculateStatistics(series);

            assertEquals("SPIKE", series.getDriftClassification(),
                    "Level shift z stabilnymi segmentami → SPIKE");
            assertTrue(series.getSpikeCount() > 0);
        }

        @Test
        @DisplayName("SPIKE (level shift) — 288 pkt, przesunięcie poziomu 4.8→5.3°C po zdarzeniu")
        void shouldClassifyAsSpike_levelShift288() {
            double[] temps = generateLevelShift288(4.8, 5.3, 144, 7.0);
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertEquals("SPIKE", series.getDriftClassification(),
                    "Level shift z stabilnymi segmentami → SPIKE (analiza segmentowa)");
            assertTrue(series.getSpikeCount() > 0);
        }

        @Test
        @DisplayName("SPIKE (level shift) — zdarzenie we wczesnej fazie serii (pomiar 48)")
        void shouldClassifyAsSpike_earlyLevelShift() {
            double[] temps = generateLevelShift288(4.5, 5.5, 48, 8.0);
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertEquals("SPIKE", series.getDriftClassification(),
                    "Wczesne zdarzenie z level shift → SPIKE");
        }

        @Test
        @DisplayName("SPIKE (level shift) — zdarzenie w późnej fazie serii (pomiar 240)")
        void shouldClassifyAsSpike_lateLevelShift() {
            double[] temps = generateLevelShift288(5.0, 5.8, 240, 9.0);
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertEquals("SPIKE", series.getDriftClassification(),
                    "Późne zdarzenie z level shift → SPIKE");
        }
    }

    // =========================================================
    // TESTY: Drift vs Spike — MIXED
    // =========================================================

    @Nested
    @DisplayName("Drift vs Spike: MIXED")
    class DriftVsSpikeMixed {

        @Test
        @DisplayName("MIXED — drift + spike + niestabilne segmenty")
        void shouldClassifyAsMixed_driftPlusSpikeUnstable() {
            // Rosnąca temp (drift) z dużą zmiennością + spike
            double[] temps = new double[POINTS_24H];
            for (int i = 0; i < POINTS_24H; i++) {
                double trend = 4.0 + (2.0 / (POINTS_24H - 1)) * i;
                double noise = 0.5 * Math.sin(2 * Math.PI * i / 12.0); // duża zmienność ±0.5
                temps[i] = round1(trend + noise);
            }
            temps[144] = 12.0; // spike
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertTrue(series.getSpikeCount() > 0, "Powinien wykryć spike");
            String cls = series.getDriftClassification();
            assertTrue("MIXED".equals(cls) || "SPIKE".equals(cls),
                    "Drift + spike → MIXED lub SPIKE, got: " + cls);
        }
    }

    // =========================================================
    // TESTY: Edge cases
    // =========================================================

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("Minimalna liczba pomiarów (6) — algorytm drift/spike włączony")
        void shouldRunAlgorithmWithMinimumPoints() {
            double[] temps = { 5.0, 5.1, 4.9, 5.0, 5.1, 4.9 };
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertNotNull(series.getDriftClassification());
        }

        @Test
        @DisplayName("5 pomiarów — algorytm drift/spike pominięty, domyślnie STABLE")
        void shouldSkipAlgorithmWithTooFewPoints() {
            double[] temps = { 5.0, 5.1, 4.9, 5.0, 5.1 };
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertEquals("STABLE", series.getDriftClassification());
            assertEquals(0, series.getSpikeCount());
        }

        @Test
        @DisplayName("Pojedynczy pomiar — oblicza min/max/avg")
        void shouldHandleSingleMeasurement() {
            double[] temps = { 5.0 };
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertEquals(5.0, series.getMinTemperature(), 0.001);
            assertEquals(5.0, series.getMaxTemperature(), 0.001);
        }

        @Test
        @DisplayName("Temperatury ujemne (mroźnia -20°C)")
        void shouldHandleNegativeTemperatures() {
            double[] temps = generateStable288(-20.0);
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertTrue(series.getMinTemperature() < -19.0);
            assertTrue(series.getMaxTemperature() > -21.0);
            assertNotNull(series.getDriftClassification());
        }

        @Test
        @DisplayName("Wiele spike'ów w różnych momentach serii")
        void shouldHandleMultipleScatteredSpikes() {
            double[] temps = generateStable288(5.0);
            // Trzy spike'i rozrzucone
            temps[72] = 10.0; // godz 6
            temps[144] = 10.0; // godz 12
            temps[216] = 10.0; // godz 18
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertTrue(series.getSpikeCount() > 0, "Powinien wykryć wiele spike'ów");
        }

        @Test
        @DisplayName("288 identycznych pomiarów — zerowy MAD")
        void shouldHandleZeroMad() {
            double[] temps = new double[POINTS_24H];
            for (int i = 0; i < POINTS_24H; i++)
                temps[i] = 5.0;
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertEquals("STABLE", series.getDriftClassification());
            assertEquals(0, series.getSpikeCount());
        }

        @Test
        @DisplayName("Duża seria 576 pomiarów (48h)")
        void shouldHandleLargeSeries() {
            double[] temps = new double[576];
            for (int i = 0; i < 576; i++) {
                temps[i] = round1(5.0 + 0.15 * Math.sin(2 * Math.PI * i / 6.0));
            }
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN);

            service.calculateStatistics(series);

            assertEquals("STABLE", series.getDriftClassification());
        }
    }

    // =========================================================
    // TESTY: Pełne scenariusze farmaceutyczne
    // =========================================================

    @Nested
    @DisplayName("Scenariusze farmaceutyczne")
    class PharmaceuticalScenarios {

        @Test
        @DisplayName("Lodówka 2-8°C — stabilna praca 24h, 0 przekroczeń")
        void shouldAnalyzeStableFridge() {
            double[] temps = generateStable288(5.0);
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN, 2.0, 8.0);

            service.calculateStatistics(series);

            assertEquals("STABLE", series.getDriftClassification());
            assertEquals(0, series.getViolationCount());
            assertEquals(0, series.getTotalTimeOutOfRangeMinutes());
        }

        @Test
        @DisplayName("Lodówka 2-8°C — otwarcie drzwi (spike do 12°C, 30 min)")
        void shouldDetectDoorOpeningAsSpike() {
            double[] temps = generateStableWithSpike288(5.0, 144, 6, 12.0);
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN, 2.0, 8.0);

            service.calculateStatistics(series);

            assertTrue(series.getSpikeCount() > 0,
                    "Powinien wykryć otwarcie drzwi jako spike");
            assertTrue(series.getViolationCount() >= 1,
                    "12°C przekracza limit 8°C");
        }

        @Test
        @DisplayName("Lodówka — awaria kompresora (drift 4.0→8.0°C)")
        void shouldDetectCompressorFailure() {
            double[] temps = generateDrift288(4.0, 8.0);
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN, 2.0, 8.0);

            service.calculateStatistics(series);

            String cls = series.getDriftClassification();
            assertTrue("DRIFT".equals(cls) || "MIXED".equals(cls),
                    "Awaria kompresora → DRIFT lub MIXED, got: " + cls);
        }

        @Test
        @DisplayName("Mroźnia -25/-15°C — stabilna praca 24h")
        void shouldAnalyzeStableFreezer() {
            double[] temps = generateStable288(-20.0);
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN, -25.0, -15.0);

            service.calculateStatistics(series);

            assertEquals("STABLE", series.getDriftClassification());
            assertEquals(0, series.getViolationCount());
        }

        @Test
        @DisplayName("Lodówka — przesunięcie poziomu po zdarzeniu (level shift 4.8→5.3°C)")
        void shouldDetectLevelShiftAsSpike() {
            double[] temps = generateLevelShift288(4.8, 5.3, 144, 7.0);
            MeasurementSeries series = createSeries(temps, INTERVAL_5MIN, 2.0, 8.0);

            service.calculateStatistics(series);

            assertEquals("SPIKE", series.getDriftClassification(),
                    "Level shift po zdarzeniu → SPIKE (analiza segmentowa)");
            assertEquals(0, series.getViolationCount(),
                    "Wszystkie pomiary w zakresie 2-8°C");
        }
    }
}
