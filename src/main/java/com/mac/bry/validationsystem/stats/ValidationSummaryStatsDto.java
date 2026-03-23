package com.mac.bry.validationsystem.stats;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * DTO dla statystyk zbiorczych walidacji.
 *
 * <p>
 * Przenosi dane z encji {@link ValidationSummaryStats} do warstwy prezentacji
 * (kontroler, widok Thymeleaf, API JSON) bez ekspozycji encji JPA.
 * </p>
 */
@Getter
@Builder
public class ValidationSummaryStatsDto {

    private final Long id;
    private final Long validationId;

    // =========================================================================
    // TABELA A — Statystyki temperatury
    // =========================================================================

    /** A.1 Minimalna temperatura globalna [°C] — coldspot w komorze. */
    private final Double globalMinTemp;

    /** A.2 Maksymalna temperatura globalna [°C] — hotspot w komorze. */
    private final Double globalMaxTemp;

    /** A.3 Średnia temperatura ważona liczbą pomiarów [°C]. */
    private final Double overallAvgTemp;

    /** A.4 Globalne odchylenie standardowe (pooled) [°C]. */
    private final Double globalStdDev;

    /** A.5 Globalny współczynnik zmienności [%]. */
    private final Double globalCvPercentage;

    /** A.6 Temperatura hotspot [°C]. */
    private final Double hotspotTemp;

    /** ID serii z hotspot. */
    private final Long hotspotSeriesId;

    /** A.7 Temperatura coldspot [°C]. */
    private final Double coldspotTemp;

    /** ID serii z coldspot. */
    private final Long coldspotSeriesId;

    /** A.8 Niepewność rozszerzona globalna [°C]. */
    private final Double globalExpandedUncertainty;

    /** A.9 Percentyl P5 [°C]. */
    private final Double globalPercentile5;

    /** A.9 Percentyl P95 [°C]. */
    private final Double globalPercentile95;

    // =========================================================================
    // TABELA B — MKT (Mean Kinetic Temperature)
    // =========================================================================

    /**
     * B.1 Globalny MKT [°C] — Arrhenius-addytywna agregacja wszystkich serii
     * siatki.
     */
    private final Double globalMkt;

    /**
     * B.2 ΔH/R użyta do obliczenia globalMkt [K] — do odtworzenia w dokumentacji.
     */
    private final Double mktDeltaHR;

    /** B.3 Najwyższy MKT spośród wszystkich serii [°C] — najgorszy przypadek. */
    private final Double mktWorstValue;

    /** ID serii z najwyższym MKT. */
    private final Long mktWorstSeriesId;

    /** B.4 Najniższy MKT spośród serii siatki [°C] — najlepszy przypadek. */
    private final Double mktBestValue;

    /** ID serii z najniższym MKT. */
    private final Long mktBestSeriesId;

    /** B.5 MKT rejestratora referencyjnego [°C] — warunki otoczenia. */
    private final Double mktReferenceValue;

    /** ID serii referencyjnej. */
    private final Long mktReferenceSeriesId;

    /**
     * B.6 Różnica globalMkt − mktReference [°C]. Wartość ujemna = komora chłodzi.
     */
    private final Double mktDeltaInternalVsReference;

    // =========================================================================
    // TABELA C — Czas w zakresie / Zgodność temperaturowa
    // =========================================================================

    /** C.1 Łączny czas w zakresie [∑ s.totalTimeInRangeMinutes] [minuty]. */
    private final Long totalTimeInRangeMinutes;

    /** C.2 Łączny czas poza zakresem [∑ s.totalTimeOutOfRangeMinutes] [minuty]. */
    private final Long totalTimeOutOfRangeMinutes;

    /** C.3 Globalny wskaźnik zgodności [%]. */
    private final Double globalCompliancePercentage;

    /** C.4 Łączna liczba przekroczeń [∑ s.violationCount]. */
    private final Integer totalViolations;

    /** C.5 Najdłuższe pojedyncze przekroczenie [minuty] — worst case. */
    private final Long maxViolationDurationMinutes;

    /** ID serii z najdłuższym przekroczeniem. */
    private final Long maxViolationSeriesId;

    /** C.6 Liczba serii z przynajmniej jednym przekroczeniem. */
    private final Integer seriesWithViolationsCount;

    /** C.7 Liczba serii w pełni zgodnych (zero przekroczeń). */
    private final Integer seriesFullyCompliantCount;

    // =========================================================================
    // TABELA D — Stabilność / Drift / Spike
    // =========================================================================

    /** D.1 Maksymalny bezwzględny trend [°C/h] — worst case drift. */
    private final Double maxAbsTrendCoefficient;

    /** ID serii z najsilniejszym driftem. */
    private final Long maxTrendSeriesId;

    /** D.2 Średnioważony współczynnik trendu [°C/h]. */
    private final Double avgTrendCoefficient;

    /** D.3 Łączna liczba spike’ów [∑ s.spikeCount]. */
    private final Integer totalSpikeCount;

    /** D.4 Liczba serii z driftem (DRIFT | MIXED). */
    private final Integer seriesWithDriftCount;

    /** D.5 Liczba serii stabilnych (STABLE). */
    private final Integer seriesStableCount;

    /** D.6 Dominująca klasyfikacja trendu (moda). */
    private final String dominantDriftClassification;

    // =========================================================================
    // METADANE WALIDACJI
    // =========================================================================

    private final Integer totalSeriesCount;
    private final Integer gridSeriesCount;
    private final Integer referenceSeriesCount;
    private final Long totalMeasurementCount;
    private final LocalDateTime validationStartTime;
    private final LocalDateTime validationEndTime;
    private final Long totalDurationMinutes;
    private final Integer dominantIntervalMinutes;
    private final LocalDateTime calculatedAt;

    /**
     * Budżet niepewności na poziomie walidacji.
     */
    private final UncertaintyBudgetDto validationUncertaintyBudget;

    // =========================================================================
    // POLA SFORMATOWANE (do widoku Thymeleaf)
    // =========================================================================

    public String getFormattedGlobalMinTemp() {
        return globalMinTemp != null ? String.format("%.2f°C", globalMinTemp) : "–";
    }

    public String getFormattedGlobalMaxTemp() {
        return globalMaxTemp != null ? String.format("%.2f°C", globalMaxTemp) : "–";
    }

    public String getFormattedOverallAvgTemp() {
        return overallAvgTemp != null ? String.format("%.2f°C", overallAvgTemp) : "–";
    }

    public String getFormattedGlobalStdDev() {
        return globalStdDev != null ? String.format("%.3f°C", globalStdDev) : "–";
    }

    public String getFormattedGlobalCvPercentage() {
        return globalCvPercentage != null ? String.format("%.2f%%", globalCvPercentage) : "–";
    }

    public String getFormattedHotspotTemp() {
        return hotspotTemp != null ? String.format("%.2f°C", hotspotTemp) : "–";
    }

    public String getFormattedColdspotTemp() {
        return coldspotTemp != null ? String.format("%.2f°C", coldspotTemp) : "–";
    }

    public String getFormattedGlobalExpandedUncertainty() {
        return globalExpandedUncertainty != null
                ? String.format("±%.3f°C", globalExpandedUncertainty)
                : "–";
    }

    public String getFormattedPercentile5() {
        return globalPercentile5 != null ? String.format("%.2f°C", globalPercentile5) : "–";
    }

    public String getFormattedPercentile95() {
        return globalPercentile95 != null ? String.format("%.2f°C", globalPercentile95) : "–";
    }

    public String getFormattedGlobalMkt() {
        return globalMkt != null ? String.format("%.2f°C", globalMkt) : "–";
    }

    public String getFormattedMktWorstValue() {
        return mktWorstValue != null ? String.format("%.2f°C", mktWorstValue) : "–";
    }

    public String getFormattedMktBestValue() {
        return mktBestValue != null ? String.format("%.2f°C", mktBestValue) : "–";
    }

    public String getFormattedMktReferenceValue() {
        return mktReferenceValue != null ? String.format("%.2f°C", mktReferenceValue) : "–";
    }

    public String getFormattedMktDelta() {
        if (mktDeltaInternalVsReference == null)
            return "–";
        String sign = mktDeltaInternalVsReference >= 0 ? "+" : "";
        return sign + String.format("%.2f°C", mktDeltaInternalVsReference);
    }

    public String getFormattedDeltaHR() {
        return mktDeltaHR != null ? String.format("%.0f K", mktDeltaHR) : "–";
    }

    // Table C — getters formatowane
    public String getFormattedCompliancePercentage() {
        return globalCompliancePercentage != null
                ? String.format("%.1f%%", globalCompliancePercentage)
                : "–";
    }

    public String getFormattedTotalTimeIn() {
        if (totalTimeInRangeMinutes == null)
            return "–";
        long h = totalTimeInRangeMinutes / 60;
        long m = totalTimeInRangeMinutes % 60;
        return h > 0 ? String.format("%dh %02dmin", h, m) : String.format("%dmin", m);
    }

    public String getFormattedTotalTimeOut() {
        if (totalTimeOutOfRangeMinutes == null)
            return "–";
        long h = totalTimeOutOfRangeMinutes / 60;
        long m = totalTimeOutOfRangeMinutes % 60;
        return h > 0 ? String.format("%dh %02dmin", h, m) : (m > 0 ? m + "min" : "0 min");
    }

    public String getFormattedMaxViolationDuration() {
        if (maxViolationDurationMinutes == null)
            return "–";
        long h = maxViolationDurationMinutes / 60;
        long m = maxViolationDurationMinutes % 60;
        return h > 0 ? String.format("%dh %02dmin", h, m) : (m > 0 ? m + "min" : "<1 min");
    }

    // Table D — getters formatowane
    public String getFormattedMaxAbsTrend() {
        if (maxAbsTrendCoefficient == null)
            return "–";
        String sign = maxAbsTrendCoefficient >= 0 ? "+" : "";
        return sign + String.format("%.3f °C/h", maxAbsTrendCoefficient);
    }

    public String getFormattedAvgTrend() {
        if (avgTrendCoefficient == null)
            return "–";
        String sign = avgTrendCoefficient >= 0 ? "+" : "";
        return sign + String.format("%.3f °C/h", avgTrendCoefficient);
    }

    public String getFormattedDominantClassification() {
        if (dominantDriftClassification == null)
            return "–";
        return switch (dominantDriftClassification) {
            case "STABLE" -> "✅ STABLE";
            case "DRIFT" -> "⚠️ DRIFT";
            case "SPIKE" -> "️ SPIKE";
            case "MIXED" -> "🟡 MIXED";
            default -> dominantDriftClassification;
        };
    }

    public String getFormattedDuration() {
        if (totalDurationMinutes == null)
            return "–";
        long h = totalDurationMinutes / 60;
        long m = totalDurationMinutes % 60;
        return h > 0 ? String.format("%dh %02dmin", h, m) : String.format("%dmin", m);
    }

    // =========================================================================
    // MAPPER
    // =========================================================================

    /**
     * Tworzy DTO z encji {@link ValidationSummaryStats}.
     */
    public static ValidationSummaryStatsDto fromEntity(ValidationSummaryStats e) {
        return ValidationSummaryStatsDto.builder()
                .id(e.getId())
                .validationId(e.getValidation() != null ? e.getValidation().getId() : null)
                // Tabela A
                .globalMinTemp(e.getGlobalMinTemp())
                .globalMaxTemp(e.getGlobalMaxTemp())
                .overallAvgTemp(e.getOverallAvgTemp())
                .globalStdDev(e.getGlobalStdDev())
                .globalCvPercentage(e.getGlobalCvPercentage())
                .hotspotTemp(e.getHotspotTemp())
                .hotspotSeriesId(e.getHotspotSeriesId())
                .coldspotTemp(e.getColdspotTemp())
                .coldspotSeriesId(e.getColdspotSeriesId())
                .globalExpandedUncertainty(e.getGlobalExpandedUncertainty())
                .globalPercentile5(e.getGlobalPercentile5())
                .globalPercentile95(e.getGlobalPercentile95())
                // Tabela B
                .globalMkt(e.getGlobalMkt())
                .mktDeltaHR(e.getMktDeltaHR())
                .mktWorstValue(e.getMktWorstValue())
                .mktWorstSeriesId(e.getMktWorstSeriesId())
                .mktBestValue(e.getMktBestValue())
                .mktBestSeriesId(e.getMktBestSeriesId())
                .mktReferenceValue(e.getMktReferenceValue())
                .mktReferenceSeriesId(e.getMktReferenceSeriesId())
                .mktDeltaInternalVsReference(e.getMktDeltaInternalVsReference())
                // Tabela C
                .totalTimeInRangeMinutes(e.getTotalTimeInRangeMinutes())
                .totalTimeOutOfRangeMinutes(e.getTotalTimeOutOfRangeMinutes())
                .globalCompliancePercentage(e.getGlobalCompliancePercentage())
                .totalViolations(e.getTotalViolations())
                .maxViolationDurationMinutes(e.getMaxViolationDurationMinutes())
                .maxViolationSeriesId(e.getMaxViolationSeriesId())
                .seriesWithViolationsCount(e.getSeriesWithViolationsCount())
                .seriesFullyCompliantCount(e.getSeriesFullyCompliantCount())
                // Tabela D
                .maxAbsTrendCoefficient(e.getMaxAbsTrendCoefficient())
                .maxTrendSeriesId(e.getMaxTrendSeriesId())
                .avgTrendCoefficient(e.getAvgTrendCoefficient())
                .totalSpikeCount(e.getTotalSpikeCount())
                .seriesWithDriftCount(e.getSeriesWithDriftCount())
                .seriesStableCount(e.getSeriesStableCount())
                .dominantDriftClassification(e.getDominantDriftClassification())
                // Metadane
                .totalSeriesCount(e.getTotalSeriesCount())
                .gridSeriesCount(e.getGridSeriesCount())
                .referenceSeriesCount(e.getReferenceSeriesCount())
                .totalMeasurementCount(e.getTotalMeasurementCount())
                .validationStartTime(e.getValidationStartTime())
                .validationEndTime(e.getValidationEndTime())
                .totalDurationMinutes(e.getTotalDurationMinutes())
                .dominantIntervalMinutes(e.getDominantIntervalMinutes())
                .calculatedAt(e.getCalculatedAt())
                .validationUncertaintyBudget(UncertaintyBudgetDto.fromEntity(e.getValidationUncertaintyBudget()))
                .build();
    }
}
