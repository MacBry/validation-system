package com.mac.bry.validationsystem.stats;

import com.mac.bry.validationsystem.measurement.UncertaintyBudget;
import com.mac.bry.validationsystem.validation.Validation;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.NotAudited;

import java.time.LocalDateTime;

/**
 * Zbiorcze statystyki walidacji obliczane ze wszystkich serii pomiarowych.
 *
 * <p>
 * Encja powiązana relacją 1:1 z {@link Validation}. Tworzona/aktualizowana
 * przez {@link ValidationSummaryStatsService} w momencie tworzenia walidacji.
 * </p>
 *
 * <h3>Tabela A — Statystyki temperatury</h3>
 * Szczegółowy opis metodologii obliczania każdej statystyki globalnej:
 * {@code docs/VALIDATION_SUMMARY_STATS_METODOLOGIA.md}
 *
 * @see ValidationSummaryStatsService
 * @see ValidationSummaryStatsServiceImpl
 */
@Entity
@Table(name = "validation_summary_stats")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationSummaryStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Walidacja, której dotyczą statystyki zbiorcze.
     * Relacja 1:1 — każda walidacja ma dokładnie jeden rekord statystyk.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validation_id", nullable = false, unique = true)
    @NotAudited
    private Validation validation;

    // =========================================================================
    // TABELA A — Statystyki temperatury (globalne)
    // =========================================================================

    /**
     * A.1 Minimalna temperatura globalna [°C].
     * min { s.minTemperature } dla serii niebędących referencyjnymi.
     * Identyfikuje "coldspot" w komorze — GDP wymóg mapowania.
     */
    @Column(name = "global_min_temp")
    private Double globalMinTemp;

    /**
     * A.2 Maksymalna temperatura globalna [°C].
     * max { s.maxTemperature } dla serii niebędących referencyjnymi.
     * Identyfikuje "hotspot" w komorze — GDP wymóg mapowania.
     */
    @Column(name = "global_max_temp")
    private Double globalMaxTemp;

    /**
     * A.3 Średnia temperatura ważona liczbą pomiarów [°C].
     * overallAvgTemp = Σ(s.avgTemperature × s.measurementCount) /
     * Σ(s.measurementCount)
     * Ważona — nie prosta średnia z avg poszczególnych serii.
     */
    @Column(name = "overall_avg_temp")
    private Double overallAvgTemp;

    /**
     * A.4 Globalne odchylenie standardowe [°C].
     * Obliczone z tożsamości Steinera dla połączonych grup (pooled variance):
     * σ²_global = [Σ(n_s × σ²_s) + Σ(n_s × (μ_s − μ_global)²)] / N_total
     * Uwzględnia zarówno wariancję wewnątrz-serii, jak i między-serii.
     */
    @Column(name = "global_std_dev")
    private Double globalStdDev;

    /**
     * A.5 Globalny współczynnik zmienności CV [%].
     * CV = (globalStdDev / |overallAvgTemp|) × 100
     * Miara względnej niejednorodności rozkładu temperatur.
     */
    @Column(name = "global_cv_percentage")
    private Double globalCvPercentage;

    /**
     * A.6 Temperatura hotspot [°C].
     * Maksymalna zmierzona temperatura wśród serii siatki (nie referencyjnych).
     * Kluczowy parametr GDP — punkt wymagający szczególnej uwagi w ocenie.
     */
    @Column(name = "hotspot_temp")
    private Double hotspotTemp;

    /**
     * ID serii pomiarowej z hotspot — do identyfikacji pozycji w siatce.
     */
    @Column(name = "hotspot_series_id")
    private Long hotspotSeriesId;

    /**
     * A.7 Temperatura coldspot [°C].
     * Minimalna zmierzona temperatura wśród serii siatki (nie referencyjnych).
     */
    @Column(name = "coldspot_temp")
    private Double coldspotTemp;

    /**
     * ID serii pomiarowej z coldspot — do identyfikacji pozycji w siatce.
     */
    @Column(name = "coldspot_series_id")
    private Long coldspotSeriesId;

    /**
     * A.8 Niepewność rozszerzona globalna [°C].
     * max { s.expandedUncertainty } — podejście konserwatywne (najgorszy
     * przypadek).
     * U = k × max(σ_s), gdzie k=2 (poziom ufności ~95%, GUM).
     */
    @Column(name = "global_expanded_uncertainty")
    private Double globalExpandedUncertainty;

    /**
     * A.9 Percentyl P5 — przybliżenie dolne [°C].
     * min { s.percentile5 } — konserwatywne przybliżenie bez dostępu do surowych
     * punktów.
     */
    @Column(name = "global_percentile5")
    private Double globalPercentile5;

    /**
     * A.9 Percentyl P95 — przybliżenie górne [°C].
     * max { s.percentile95 } — konserwatywne przybliżenie.
     */
    @Column(name = "global_percentile95")
    private Double globalPercentile95;

    // =========================================================================
    // TABELA B — MKT (Mean Kinetic Temperature)
    // =========================================================================

    /**
     * B.1 Globalny MKT [°C] — obliczony ze wszystkich serii siatki.
     * Wyprowadzony z tożsamości Arrheniusa dla połączonych serii:
     * MKT_global = (ΔH/R) / (-ln(Σ(n_s × e^(-ΔH/(R×MKT_s_K))) / N)) − 273.15
     * Nie jest to średnia MKT per seria — MKT nie jest addytywny.
     */
    @Column(name = "global_mkt")
    private Double globalMkt;

    /**
     * B.2 Wartość ΔH/R użyta przy obliczaniu globalMkt [K].
     * Pozwala odtworzyć obliczenie w dokumentacji.
     * deltaH_r = activationEnergy [J/mol] / R [J/(mol·K)]
     */
    @Column(name = "mkt_delta_h_r")
    private Double mktDeltaHR;

    /**
     * B.3 Najwyższy MKT spośród wszystkich serii [°C] — najgorszy przypadek.
     * max { s.mktTemperature | s ∈ allSeries }
     */
    @Column(name = "mkt_worst_value")
    private Double mktWorstValue;

    /** ID serii z najwyższym MKT — do identyfikacji w protokole. */
    @Column(name = "mkt_worst_series_id")
    private Long mktWorstSeriesId;

    /**
     * B.4 Najniższy MKT spośród serii siatki [°C] — najlepszy przypadek.
     * min { s.mktTemperature | s ∈ gridSeries }
     */
    @Column(name = "mkt_best_value")
    private Double mktBestValue;

    /** ID serii z najniższym MKT. */
    @Column(name = "mkt_best_series_id")
    private Long mktBestSeriesId;

    /**
     * B.5 MKT rejestratora referencyjnego [°C].
     * MKT serii z isReferenceRecorder=true (jeśli istnieje).
     * Służy do porównania warunków wewnątrz komory vs. otoczenie.
     */
    @Column(name = "mkt_reference_value")
    private Double mktReferenceValue;

    /** ID serii referencyjnej użytej do mkt_reference_value. */
    @Column(name = "mkt_reference_series_id")
    private Long mktReferenceSeriesId;

    /**
     * B.6 Różnica MKT: globalMkt − mktReferenceValue [°C].
     * Wskazuje jak bardzo warunki wewnątrz komory odbiegają od otoczenia.
     * Wartość ujemna = komora chłodząca spełnia rolę.
     */
    @Column(name = "mkt_delta_internal_vs_reference")
    private Double mktDeltaInternalVsReference;

    // =========================================================================
    // TABELA C — Czas w zakresie / Zgodność temperaturowa (Compliance)
    // =========================================================================

    /**
     * C.1 Łączny czas w zakresie [∑ s.totalTimeInRangeMinutes] [minuty].
     * Suma czasów w zakresie ze wszystkich serii (siatka + referencyjne).
     * Wyższy = lepszy.
     */
    @Column(name = "total_time_in_range_minutes")
    private Long totalTimeInRangeMinutes;

    /**
     * C.2 Łączny czas poza zakresem [∑ s.totalTimeOutOfRangeMinutes] [minuty].
     * Suma czasów przekroczeń ze wszystkich serii.
     * 0 = idealna zgodność; >0 = konieczna analiza odchyleń.
     */
    @Column(name = "total_time_out_of_range_minutes")
    private Long totalTimeOutOfRangeMinutes;

    /**
     * C.3 Globalny współczynnik zgodności [%].
     * compliance = totalTimeInRange / (totalTimeInRange + totalTimeOutOfRange) ×
     * 100
     * Wymagane: >= 95% dla większości scenariuszy GMP.
     */
    @Column(name = "global_compliance_percentage")
    private Double globalCompliancePercentage;

    /**
     * C.4 Łączna liczba przekroczeń zakresu [∑ s.violationCount].
     * Suma wszystkich incydentów ze wszystkich serii.
     * 0 = brak przekroczeń.
     */
    @Column(name = "total_violations")
    private Integer totalViolations;

    /**
     * C.5 Najdłuższe pojedyncze przekroczenie zakresu [minuty] — worst case.
     * max { s.maxViolationDurationMinutes | s ∈ allSeries }
     * Kluczowy parametr "worst-case excursion" w protokole GMP.
     */
    @Column(name = "max_violation_duration_minutes")
    private Long maxViolationDurationMinutes;

    /** ID serii z najdłuższym przekroczeniem (do identyfikacji w protokole). */
    @Column(name = "max_violation_series_id")
    private Long maxViolationSeriesId;

    /**
     * C.6 Liczba serii z przynajmniej jednym przekroczeniem.
     * count { s | s.violationCount > 0 }
     * Wskazuje zasięg problemów termicznych w przestrzeni komory.
     */
    @Column(name = "series_with_violations_count")
    private Integer seriesWithViolationsCount;

    /**
     * C.7 Liczba serii w pełni zgodnych (0 przekroczeń).
     * count { s | s.violationCount == 0 }
     * Uzupełnienie C.6 — razem dają pełny obraz zgodności.
     */
    @Column(name = "series_fully_compliant_count")
    private Integer seriesFullyCompliantCount;

    // =========================================================================
    // TABELA D — Stabilność / Drift / Spike (Analiza trendu)
    // =========================================================================

    /**
     * D.1 Maksymalny bezwzględny współczynnik trendu [°C/h] — worst case drift.
     * max { |s.trendCoefficient| | s ∈ allSeries, s.trendCoefficient != null }
     * Wskazuje serię wykazującą najsilniejszy (najgorszy) dryftu.
     */
    @Column(name = "max_abs_trend_coefficient")
    private Double maxAbsTrendCoefficient;

    /** ID serii z najwyższym |trend| — do identyfikacji w protokole. */
    @Column(name = "max_trend_series_id")
    private Long maxTrendSeriesId;

    /**
     * D.2 Średnioważony współczynnik trendu [°C/h].
     * Średnioważona trendCoefficient liczbą pomiarów [s.measurementCount].
     * Wartość bliska 0 = brak globalnego trendu temperaturowego.
     */
    @Column(name = "avg_trend_coefficient")
    private Double avgTrendCoefficient;

    /**
     * D.3 Łączna liczba spike’ów [∑ s.spikeCount].
     * Suma wszystkich anomalii impulsowych ze wszystkich serii.
     * Spike = krótkotrwała anomalia statystyczna (|T_i - mean| > 3σ).
     */
    @Column(name = "total_spike_count")
    private Integer totalSpikeCount;

    /**
     * D.4 Liczba serii sklasyfikowanych jako DRIFT lub MIXED.
     * count { s | s.driftClassification IN ('DRIFT', 'MIXED') }
     * Wskazuje ile rejestratorów wykazuje systematyczny dryftu.
     */
    @Column(name = "series_with_drift_count")
    private Integer seriesWithDriftCount;

    /**
     * D.5 Liczba serii stabilnych (STABLE).
     * count { s | s.driftClassification = 'STABLE' }
     * Idealna walidacja: wszystkie serie = STABLE.
     */
    @Column(name = "series_stable_count")
    private Integer seriesStableCount;

    /**
     * D.6 Dominująca klasyfikacja driftu — moda z { s.driftClassification }.
     * STABLE | DRIFT | SPIKE | MIXED
     * Syntetyczna ocena jakości termicznej walidacji.
     */
    @Column(name = "dominant_drift_classification", length = 10)
    private String dominantDriftClassification;

    // =========================================================================
    // METADANE WALIDACJI (Tabela E — podstawowe)
    // =========================================================================

    /** Łączna liczba serii pomiarowych (siatka + referencyjne). */
    @Column(name = "total_series_count")
    private Integer totalSeriesCount;

    /** Liczba serii siatki (isReferenceRecorder = false). */
    @Column(name = "grid_series_count")
    private Integer gridSeriesCount;

    /** Liczba serii referencyjnych (isReferenceRecorder = true). */
    @Column(name = "reference_series_count")
    private Integer referenceSeriesCount;

    /** Łączna liczba pomiarów ze wszystkich serii: Σ s.measurementCount. */
    @Column(name = "total_measurement_count")
    private Long totalMeasurementCount;

    /** Czas pierwszego pomiaru w całej walidacji: min(s.firstMeasurementTime). */
    @Column(name = "validation_start_time")
    private LocalDateTime validationStartTime;

    /** Czas ostatniego pomiaru w całej walidacji: max(s.lastMeasurementTime). */
    @Column(name = "validation_end_time")
    private LocalDateTime validationEndTime;

    /** Czas trwania całej sesji mapowania [minuty]. */
    @Column(name = "total_duration_minutes")
    private Long totalDurationMinutes;

    /**
     * Dominujący interwał pomiarowy [minuty].
     * Moda z { s.measurementIntervalMinutes } — najczęściej występujący interwał.
     */
    @Column(name = "dominant_interval_minutes")
    private Integer dominantIntervalMinutes;

    /**
     * Budżet niepewności na poziomie walidacji (agregowany z wszystkich serii siatki).
     * Zawiera komponenty Typ A, Typ B i dodatkowy komponent przestrzenny.
     * Zgodny z GUM (JCGM 100:2008) dla pełnej zgodności z ISO/IEC 17025.
     */
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "validation_uncertainty_budget_id")
    @NotAudited
    private UncertaintyBudget validationUncertaintyBudget;

    /** Data i godzina ostatniego obliczenia statystyk zbiorczych. */
    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;
}
