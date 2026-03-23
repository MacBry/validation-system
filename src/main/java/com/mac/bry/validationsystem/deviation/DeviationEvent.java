package com.mac.bry.validationsystem.deviation;

import com.mac.bry.validationsystem.measurement.MeasurementSeries;
import com.mac.bry.validationsystem.validation.Validation;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Pojedyncze naruszenie granicy temperaturowej wykryte automatycznie
 * z danych pomiarowych (MeasurementPoint).
 *
 * <p>
 * Powiązane z walidacją i serią pomiarową, w której wystąpiło.
 * Klasyfikowane automatycznie wg ciężkości (CRITICAL / MAJOR / MINOR)
 * na podstawie czasu trwania i odchylenia temperatury od granicy.
 * </p>
 */
@Entity
@Table(name = "deviation_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validation_id", nullable = false)
    private Validation validation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "measurement_series_id", nullable = false)
    private MeasurementSeries measurementSeries;

    /** Początek naruszenia */
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    /** Koniec naruszenia */
    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    /** Czas trwania naruszenia [minuty] */
    @Column(name = "duration_minutes", nullable = false)
    private Long durationMinutes;

    /** Temperatura szczytowa podczas naruszenia [°C] */
    @Column(name = "peak_temperature", nullable = false)
    private Double peakTemperature;

    /** Granica, która została przekroczona [°C] */
    @Column(name = "violated_limit", nullable = false)
    private Double violatedLimit;

    /** Typ naruszenia (powyżej górnej / poniżej dolnej granicy) */
    @Enumerated(EnumType.STRING)
    @Column(name = "violation_type", nullable = false, length = 20)
    private ViolationType violationType;

    /** Klasyfikacja ciężkości (CRITICAL / MAJOR / MINOR) */
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private DeviationSeverity severity;

    /** Analiza użytkownika (root cause, impact, CAPA) — opcjonalna */
    @OneToOne(mappedBy = "deviationEvent", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private DeviationAnalysis analysis;

    // =========================================================================
    // POMOCNICZE METODY WIDOKOWE
    // =========================================================================

    public String getFormattedDuration() {
        if (durationMinutes == null)
            return "–";
        long h = durationMinutes / 60;
        long m = durationMinutes % 60;
        return h > 0 ? String.format("%dh %02dmin", h, m) : String.format("%dmin", m);
    }

    public String getFormattedPeakTemp() {
        return peakTemperature != null ? String.format("%.2f°C", peakTemperature) : "–";
    }

    public String getFormattedViolatedLimit() {
        return violatedLimit != null ? String.format("%.1f°C", violatedLimit) : "–";
    }

    /** Różnica między temperaturą szczytową a przekroczoną granicą */
    public String getFormattedDeltaTemp() {
        if (peakTemperature == null || violatedLimit == null)
            return "–";
        double delta = Math.abs(peakTemperature - violatedLimit);
        return String.format("%.2f°C", delta);
    }

    /** Pozycja rejestratora (np. TOP_LEFT_FRONT) */
    public String getSeriesPositionName() {
        if (measurementSeries == null || measurementSeries.getRecorderPosition() == null)
            return "–";
        return measurementSeries.getRecorderPosition().getDisplayName();
    }
}
