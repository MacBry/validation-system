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
            "Badanie rozkładu temperatur w urządzeniu bez testów OQ/PQ");

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
            default:
                return "❓";
        }
    }
}
