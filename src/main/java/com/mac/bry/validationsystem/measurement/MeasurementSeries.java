package com.mac.bry.validationsystem.measurement;

import com.mac.bry.validationsystem.device.CoolingDevice;
import com.mac.bry.validationsystem.thermorecorder.ThermoRecorder;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Encja reprezentująca serię pomiarów z rejestratora Testo
 */
@Entity
@Table(name = "measurement_series")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeasurementSeries {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Numer seryjny rejestratora z którego pochodzą dane
     */
    @Column(name = "recorder_serial_number", length = 50)
    private String recorderSerialNumber;

    /**
     * Oryginalna nazwa pliku .vi2
     */
    @Column(name = "original_filename", length = 255, nullable = false)
    private String originalFilename;

    /**
     * Data i godzina pierwszego pomiaru
     */
    @Column(name = "first_measurement_time", nullable = false)
    private LocalDateTime firstMeasurementTime;

    /**
     * Data i godzina ostatniego pomiaru
     */
    @Column(name = "last_measurement_time", nullable = false)
    private LocalDateTime lastMeasurementTime;

    /**
     * Minimalna temperatura w serii [°C]
     */
    @Column(name = "min_temperature", nullable = false)
    private Double minTemperature;

    /**
     * Maksymalna temperatura w serii [°C]
     */
    @Column(name = "max_temperature", nullable = false)
    private Double maxTemperature;

    /**
     * Średnia temperatura w serii [°C]
     */
    @Column(name = "avg_temperature", nullable = false)
    private Double avgTemperature;

    /**
     * Odchylenie standardowe temperatury [°C]
     */
    @Column(name = "std_deviation")
    private Double stdDeviation;

    /**
     * Wariancja temperatury [°C²]
     */
    @Column(name = "variance")
    private Double variance;

    /**
     * Średnia temperatura kinetyczna (Mean Kinetic Temperature) [°C]
     */
    @Column(name = "mkt_temperature")
    private Double mktTemperature;

    /**
     * Współczynnik zmienności (Coefficient of Variation) [%]
     */
    @Column(name = "cv_percentage")
    private Double cvPercentage;

    /**
     * Mediana temperatury [°C]
     */
    @Column(name = "median_temperature")
    private Double medianTemperature;

    /**
     * Niepewność rozszerzona U = k × stdDev (k=2, 95% poziomu ufności) [°C]
     */
    @Column(name = "expanded_uncertainty")
    private Double expandedUncertainty;

    /**
     * Percentyl 5 — 5% najniższych pomiarów poniżej tej wartości [°C]
     */
    @Column(name = "percentile_5")
    private Double percentile5;

    /**
     * Percentyl 95 — 95% pomiarów poniżej tej wartości [°C]
     */
    @Column(name = "percentile_95")
    private Double percentile95;

    /**
     * Całkowity czas poza zakresem [minuty]
     */
    @Column(name = "time_out_of_range_min")
    private Long totalTimeOutOfRangeMinutes;

    /**
     * Całkowity czas w zakresie [minuty]
     */
    @Column(name = "time_in_range_min")
    private Long totalTimeInRangeMinutes;

    /**
     * Liczba przekroczeń zakresu
     */
    @Column(name = "violation_count")
    private Integer violationCount;

    /**
     * Maksymalny czas powrotu do zakresu (czas trwania najdłuższego przekroczenia)
     * [minuty]
     */
    /**
     * Współczynnik trendu (slope) z regresji liniowej [°C/godzina]
     */
    @Column(name = "trend_coefficient")
    private Double trendCoefficient;

    /**
     * Skorygowany współczynnik trendu (bez spike'ów) [°C/godzina]
     */
    @Column(name = "adj_trend_coefficient")
    private Double adjustedTrendCoefficient;

    /**
     * Klasyfikacja trendu (DRIFT, SPIKE, STABLE, MIXED)
     */
    @Column(name = "drift_classification")
    private String driftClassification;

    /**
     * Liczba wykrytych spike'ów
     */
    @Column(name = "spike_count")
    private Integer spikeCount;

    /**
     * Maksymalny czas powrotu do zakresu (czas trwania najdłuższego przekroczenia)
     * [minuty]
     */
    @Column(name = "max_violation_duration_min")
    private Long maxViolationDurationMinutes;

    /**
     * Umiejscowienie rejestratora w urządzeniu chłodniczym.
     * Wymagane przy wgrywaniu pliku .vi2.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "recorder_position", length = 30)
    private RecorderPosition recorderPosition;

    /**
     * Czy to rejestrator referencyjny (zewnętrzny, poza siatką 3×3×3).
     */
    @Column(name = "is_reference_recorder", nullable = false)
    private Boolean isReferenceRecorder = false;

    /**
     * Urządzenie chłodnicze do którego przypisana jest seria pomiarowa.
     * Ustawiane przy wgrywaniu pliku .vi2.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cooling_device_id")
    private CoolingDevice coolingDevice;

    /**
     * Rejestrator TESTO, z którego pochodzą dane.
     * Przypisywany automatycznie na podstawie numeru seryjnego z pliku .vi2.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thermo_recorder_id")
    private ThermoRecorder thermoRecorder;

    /**
     * Budżet niepewności GUM dla tej serii pomiarowej
     * Zawiera wszystkie komponenty niepewności Typ A i Typ B
     */
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "uncertainty_budget_id")
    private UncertaintyBudget uncertaintyBudget;

    /**
     * Dolna granica alarmu [°C]
     */
    @Column(name = "lower_limit")
    private Double lowerLimit;

    /**
     * Górna granica alarmu [°C]
     */
    @Column(name = "upper_limit")
    private Double upperLimit;

    /**
     * Liczba pomiarów w serii
     */
    @Column(name = "measurement_count", nullable = false)
    private Integer measurementCount;

    /**
     * Interwał między pomiarami w minutach
     */
    @Column(name = "measurement_interval_minutes")
    private Integer measurementIntervalMinutes;

    /**
     * Czy seria pomiarowa została już użyta w walidacji
     * true = już użyta (nie można użyć ponownie)
     * false = wolna (można użyć do nowej walidacji)
     */
    @Column(name = "used_in_validation", nullable = false)
    private Boolean usedInValidation = false;

    /**
     * Data przesłania pliku
     */
    @Column(name = "upload_date", nullable = false)
    private LocalDateTime uploadDate;

    /**
     * Lista wszystkich punktów pomiarowych (timestamp + temperatura)
     */
    @OneToMany(mappedBy = "series", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("measurementTime ASC")
    private List<MeasurementPoint> measurementPoints = new ArrayList<>();

    /**
     * Pomocnicza metoda do dodawania punktu pomiarowego
     */
    public void addMeasurementPoint(MeasurementPoint point) {
        measurementPoints.add(point);
        point.setSeries(this);
    }

    public String getFormattedMinTemperature() {
        return minTemperature != null ? String.format("%.1f°C", minTemperature) : "–";
    }

    public String getFormattedMaxTemperature() {
        return maxTemperature != null ? String.format("%.1f°C", maxTemperature) : "–";
    }

    public String getFormattedAvgTemperature() {
        return avgTemperature != null ? String.format("%.1f°C", avgTemperature) : "–";
    }

    /**
     * Zwraca sformatowaną średnią temperaturę kinetyczną
     */
    public String getFormattedMktTemperature() {
        return mktTemperature != null ? String.format("%.1f°C", mktTemperature) : "–";
    }

    /**
     * Zwraca sformatowany czas trwania pomiaru
     */
    public String getFormattedDuration() {
        if (firstMeasurementTime == null || lastMeasurementTime == null) {
            return "–";
        }

        long hours = java.time.Duration.between(firstMeasurementTime, lastMeasurementTime).toHours();
        long minutes = java.time.Duration.between(firstMeasurementTime, lastMeasurementTime).toMinutes() % 60;

        if (hours > 0) {
            return String.format("%dh %dmin", hours, minutes);
        } else {
            return String.format("%dmin", minutes);
        }
    }

    /**
     * Zwraca liczbę punktów danych jako Long (dla ValidationUncertaintyBudgetService)
     */
    public Long getDataPointsCount() {
        return measurementCount != null ? measurementCount.longValue() : 0L;
    }
}
