package com.mac.bry.validationsystem.stats;

import com.mac.bry.validationsystem.measurement.UncertaintyBudget;
import com.mac.bry.validationsystem.measurement.UncertaintyBudgetType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * DTO dla budżetu niepewności.
 */
@Getter
@Builder
public class UncertaintyBudgetDto {

    private final Long id;
    private final Double statisticalUncertainty;
    private final Double calibrationUncertainty;
    private final Double resolutionUncertainty;
    private final Double systematicUncertainty;
    private final Double stabilityUncertainty;
    private final Double spatialUncertainty;
    private final Double combinedUncertainty;
    private final Double expandedUncertainty;
    private final Double coverageFactor;
    private final Double confidenceLevel;
    private final Integer degreesOfFreedom;
    private final UncertaintyBudgetType budgetType;
    private final String calculationNotes;
    private final LocalDateTime createdAt;

    /**
     * Zwraca główny komponent niepewności (dominant uncertainty source).
     * Odwzorowuje logikę z encji UncertaintyBudget.
     */
    public String getDominantUncertaintySource() {
        double uA = statisticalUncertainty != null ? statisticalUncertainty : 0.0;
        double uCal = calibrationUncertainty != null ? calibrationUncertainty : 0.0;
        double uRes = resolutionUncertainty != null ? resolutionUncertainty : 0.0;
        double uSys = systematicUncertainty != null ? systematicUncertainty : 0.0;
        double uStab = stabilityUncertainty != null ? stabilityUncertainty : 0.0;
        double uSpat = spatialUncertainty != null ? spatialUncertainty : 0.0;

        double max = Math.max(uA, Math.max(uCal, Math.max(uRes, Math.max(uSys, uStab))));
        if (uSpat > max) {
            max = uSpat;
        }

        if (max <= 0)
            return "Brak danych";
        if (max == uA)
            return "Statystyczna (Typ A)";
        if (max == uCal)
            return "Wzorcowanie (Typ B)";
        if (max == uRes)
            return "Rozdzielczość (Typ B)";
        if (max == uSys)
            return "Systematyczna (Typ B)";
        if (max == uStab)
            return "Stabilność (Typ B)";
        if (max == uSpat)
            return "Przestrzenna (Typ B)";

        return "Nieznane";
    }

    public static UncertaintyBudgetDto fromEntity(UncertaintyBudget e) {
        if (e == null)
            return null;
        return UncertaintyBudgetDto.builder()
                .id(e.getId())
                .statisticalUncertainty(e.getStatisticalUncertainty())
                .calibrationUncertainty(e.getCalibrationUncertainty())
                .resolutionUncertainty(e.getResolutionUncertainty())
                .systematicUncertainty(e.getSystematicUncertainty())
                .stabilityUncertainty(e.getStabilityUncertainty())
                .spatialUncertainty(e.getSpatialUncertainty())
                .combinedUncertainty(e.getCombinedUncertainty())
                .expandedUncertainty(e.getExpandedUncertainty())
                .coverageFactor(e.getCoverageFactor())
                .confidenceLevel(e.getConfidenceLevel())
                .degreesOfFreedom(e.getDegreesOfFreedom())
                .budgetType(e.getBudgetType())
                .calculationNotes(e.getCalculationNotes())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
