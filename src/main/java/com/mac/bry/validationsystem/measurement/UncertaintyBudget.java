package com.mac.bry.validationsystem.measurement;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Encja reprezentująca budżet niepewności zgodnie z GUM (Guide to the
 * Expression of Uncertainty in Measurement).
 * Zawiera wszystkie komponenty niepewności Typu A i B oraz ich kombinację w
 * niepewność rozszerzoną.
 *
 * GMP COMPLIANCE NOTES:
 * - ISO/IEC 17025 - wymagana dokumentacja niepewności pomiarowej
 * - GUM (JCGM 100:2008) - standardowy sposób łączenia niepewności
 * - FDA 21 CFR Part 11 - wymaga walidacji systemu pomiarowego
 * - ICH Q2(R1) - walidacja metod analitycznych
 */
@Entity
@Table(name = "uncertainty_budgets")
@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UncertaintyBudget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Niepewność statystyczna (Typ A) - obliczona z powtarzalności pomiarów
     * u_A = σ/√n, gdzie σ = odchylenie standardowe, n = liczba pomiarów
     */
    @Column(name = "statistical_uncertainty", nullable = false)
    private Double statisticalUncertainty;

    /**
     * Niepewność kalibracji (Typ B) - z certyfikatu wzorcowania rejestratora
     * Pobierana z CalibrationPoint.uncertainty dla odpowiedniej temperatury
     */
    @Column(name = "calibration_uncertainty", nullable = false)
    private Double calibrationUncertainty;

    /**
     * Niepewność rozdzielczości (Typ B) - ograniczona przez cyfrową rozdzielczość
     * u_res = rozdzielczość / (2 * √3) dla rozkładu prostokątnego
     */
    @Column(name = "resolution_uncertainty", nullable = false)
    private Double resolutionUncertainty;

    /**
     * Niepewność systematyczna (Typ B) - błąd systematyczny z kalibracji
     * u_sys = |systematicError| / √3 dla rozkładu prostokątnego bias
     */
    @Column(name = "systematic_uncertainty", nullable = false)
    private Double systematicUncertainty;

    /**
     * Niepewność stabilności długoterminowej (Typ B) - drift między kalibracjami
     * u_stab = drift_annual * (t_measurement / 365) / √3
     */
    @Column(name = "stability_uncertainty", nullable = false)
    private Double stabilityUncertainty;

    /**
     * Niepewność przestrzenna (Typ B) - różnice między pozycjami w urządzeniu
     * Tylko dla budżetu na poziomie walidacji, null dla pojedynczej serii
     */
    @Column(name = "spatial_uncertainty", nullable = true)
    private Double spatialUncertainty;

    /**
     * Niepewność łączna (kombinowana) - u_c = √(suma kwadratów wszystkich
     * komponentów)
     * u_c = √(u_A² + u_B1² + u_B2² + ... + u_Bn²)
     */
    @Column(name = "combined_uncertainty", nullable = false)
    private Double combinedUncertainty;

    /**
     * Niepewność rozszerzona - U = k * u_c
     * Typowo k=2 dla ~95% poziomu ufności (rozkład normalny)
     */
    @Column(name = "expanded_uncertainty", nullable = false)
    private Double expandedUncertainty;

    /**
     * Współczynnik pokrycia k - typowo 2.0 dla 95% poziomu ufności
     * Może być obliczony z rozkładu t-Studenta dla małych stopni swobody
     */
    @Column(name = "coverage_factor", nullable = false)
    private Double coverageFactor;

    /**
     * Poziom ufności w procentach - typowo 95.45% dla k=2 (rozkład normalny)
     */
    @Column(name = "confidence_level", nullable = false)
    private Double confidenceLevel;

    /**
     * Efektywne stopnie swobody - obliczone według wzoru Welch-Satterthwaite
     * ν_eff ≈ u_c⁴ / Σ(u_i⁴/ν_i) dla prawidłowego współczynnika pokrycia
     */
    @Column(name = "degrees_of_freedom", nullable = true)
    private Integer degreesOfFreedom;

    /**
     * Typ budżetu niepewności - SERIES dla pojedynczej serii, VALIDATION dla całej
     * walidacji
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "budget_type", length = 20, nullable = false)
    private UncertaintyBudgetType budgetType;

    /**
     * Opis kontekstu obliczenia budżetu (opcjonalny)
     */
    @Column(name = "calculation_notes", length = 1000)
    private String calculationNotes;

    /**
     * Data utworzenia budżetu niepewności
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    /**
     * Weryfikuje czy budżet niepewności jest kompletny i spójny
     */
    public boolean isValid() {
        if (statisticalUncertainty == null || statisticalUncertainty < 0)
            return false;
        if (calibrationUncertainty == null || calibrationUncertainty < 0)
            return false;
        if (resolutionUncertainty == null || resolutionUncertainty < 0)
            return false;
        if (systematicUncertainty == null || systematicUncertainty < 0)
            return false;
        if (stabilityUncertainty == null || stabilityUncertainty < 0)
            return false;
        if (combinedUncertainty == null || combinedUncertainty <= 0)
            return false;
        if (expandedUncertainty == null || expandedUncertainty <= 0)
            return false;
        if (coverageFactor == null || coverageFactor <= 0)
            return false;
        if (confidenceLevel == null || confidenceLevel <= 0 || confidenceLevel > 100)
            return false;

        // Sprawdź zgodność RSS kombinacji
        double expectedCombined = calculateExpectedCombinedUncertainty();
        double tolerance = 0.001; // 0.1% tolerancja na zaokrąglenia
        return Math.abs(combinedUncertainty - expectedCombined) / expectedCombined < tolerance;
    }

    /**
     * Oblicza oczekiwaną niepewność kombinowaną dla walidacji
     */
    private double calculateExpectedCombinedUncertainty() {
        double sum = Math.pow(statisticalUncertainty, 2) +
                Math.pow(calibrationUncertainty, 2) +
                Math.pow(resolutionUncertainty, 2) +
                Math.pow(systematicUncertainty, 2) +
                Math.pow(stabilityUncertainty, 2);

        if (spatialUncertainty != null) {
            sum += Math.pow(spatialUncertainty, 2);
        }

        return Math.sqrt(sum);
    }

    /**
     * Zwraca główny komponent niepewności (dominant uncertainty source)
     */
    public String getDominantUncertaintySource() {
        double max = -1.0;
        String source = "Unknown";

        if (statisticalUncertainty != null && statisticalUncertainty > max) {
            max = statisticalUncertainty;
            source = "Statistical (Type A)";
        }
        if (calibrationUncertainty != null && calibrationUncertainty > max) {
            max = calibrationUncertainty;
            source = "Calibration (Type B)";
        }
        if (resolutionUncertainty != null && resolutionUncertainty > max) {
            max = resolutionUncertainty;
            source = "Resolution (Type B)";
        }
        if (systematicUncertainty != null && systematicUncertainty > max) {
            max = systematicUncertainty;
            source = "Systematic (Type B)";
        }
        if (stabilityUncertainty != null && stabilityUncertainty > max) {
            max = stabilityUncertainty;
            source = "Stability (Type B)";
        }
        if (spatialUncertainty != null && spatialUncertainty > max) {
            max = spatialUncertainty;
            source = "Spatial (Type B)";
        }

        return source;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        UncertaintyBudget that = (UncertaintyBudget) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "UncertaintyBudget{" +
                "id=" + id +
                ", type=" + budgetType +
                ", statistical=" + statisticalUncertainty +
                ", calibration=" + calibrationUncertainty +
                ", combined=" + combinedUncertainty +
                ", expanded=" + expandedUncertainty +
                ", k=" + coverageFactor +
                '}';
    }
}