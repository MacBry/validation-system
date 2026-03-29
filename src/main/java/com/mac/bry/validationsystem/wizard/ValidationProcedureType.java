package com.mac.bry.validationsystem.wizard;

/**
 * Typ procedury kwalifikacji urządzenia chłodzącego.
 *
 * <p>
 * Definiuje trzy główne ścieżki walidacji zgodnie ze standardami GMP:
 * - OQ: Operational Qualification - sprawdzenie działania urządzenia
 * - PQ: Performance Qualification - sprawdzenie wydajności w rzeczywistych warunkach
 * - MAPPING: Mapowanie pola temperaturowego (bez testów OQ/PQ)
 * </p>
 */
public enum ValidationProcedureType {

    /**
     * Operational Qualification (OQ)
     *
     * <p>
     * Kwalifikacja operacyjna - sprawdzenie czy urządzenie działa prawidłowo.
     * Obejmuje testy: awaria zasilania, weryfikacja alarmu, test drzwi.
     * Generuje specjalne sekcje PDF z wynikami testów.
     * </p>
     */
    OQ("OQ - Kwalifikacja Operacyjna",
       "Sprawdzenie prawidłowości działania urządzenia: awaria zasilania, alarm, drzwi"),

    /**
     * Performance Qualification (PQ)
     *
     * <p>
     * Kwalifikacja wydajności - sprawdzenie czy urządzenie spełnia wymagania
     * w rzeczywistych warunkach roboczych z produktem.
     * Obejmuje 10-punktową listę kontrolną (PQ-01..PQ-10).
     * </p>
     */
    PQ("PQ - Kwalifikacja Wydajności",
       "Sprawdzenie wydajności urządzenia w rzeczywistych warunkach roboczych"),

    /**
     * Temperature Mapping
     *
     * <p>
     * Mapowanie pola temperaturowego - badanie rozkładu temperatur
     * w przestrzeni urządzenia bez procedur OQ/PQ.
     * Generuje standardowy protokół walidacji.
     * </p>
     */
    MAPPING("Mapowanie - Pole Temperaturowe",
            "Badanie rozkładu temperatur w urządzeniu bez testów OQ/PQ"),

    /**
     * Periodic Revalidation
     *
     * <p>
     * Rewalidacja okresowa - dwufazowy proces (Plan + Pomiary)
     * wymagający zatwierdzenia QA między fazami zgodnie z GMP Annex 15 §10.
     * Faza 1 (kroki 1-8): Plan walidacji z podpisem technologa i zatwierdzeniem QA.
     * Faza 2 (kroki 9-13): Pomiary i finalizacja po zatwierdzeniu planu.
     * </p>
     */
    PERIODIC_REVALIDATION("Rewalidacja Okresowa",
                          "Dwufazowy proces: plan walidacji + pomiary, wymagający zatwierdzenia QA");

    private final String displayName;
    private final String description;

    ValidationProcedureType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Sprawdza czy procedura wymaga testów OQ
     */
    public boolean requiresOqTests() {
        return this == OQ;
    }

    /**
     * Sprawdza czy procedura wymaga listy kontrolnej PQ
     */
    public boolean requiresPqChecklist() {
        return this == PQ;
    }

    /**
     * Sprawdza czy procedura wymaga planu walidacji z zatwierdzeniem QA
     * (dwufazowy wizard: plan + pomiary)
     */
    public boolean requiresValidationPlan() {
        return this == PERIODIC_REVALIDATION;
    }

    /**
     * Sprawdza czy procedura jest dwufazowa (plan + pomiary)
     */
    public boolean isTwoPhaseWizard() {
        return this == PERIODIC_REVALIDATION;
    }

    /**
     * Zwraca emoji dla typu procedury
     */
    public String getIcon() {
        switch (this) {
            case OQ:
                return "⚙️"; // Operational
            case PQ:
                return "📊"; // Performance
            case MAPPING:
                return "🗺️"; // Mapping
            case PERIODIC_REVALIDATION:
                return "🔄"; // Periodic
            default:
                return "❓";
        }
    }
}
