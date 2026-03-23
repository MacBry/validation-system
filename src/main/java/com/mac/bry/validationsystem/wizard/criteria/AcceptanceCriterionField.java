package com.mac.bry.validationsystem.wizard.criteria;

/**
 * Pola danych walidacji, które mogą być użyte w kryteriach akceptacji.
 *
 * <p>
 * Mapuje dostępne metryki statystyczne z walidacji:
 * - Temperatury: MIN, MAX, AVERAGE, MKT
 * - Zmienność: STD_DEV, DRIFT (trend)
 * - Zgodność: COMPLIANCE_PERCENTAGE, MAX_VIOLATION_DURATION, TOTAL_VIOLATION_COUNT
 * </p>
 */
public enum AcceptanceCriterionField {

    /**
     * Minimalna temperatura zarejestrowana
     */
    MIN_TEMP("Temperatura minimalna",
             "Najniższa temperatura zarejestrowana podczas serii",
             "°C"),

    /**
     * Maksymalna temperatura zarejestrowana
     */
    MAX_TEMP("Temperatura maksymalna",
             "Najwyższa temperatura zarejestrowana podczas serii",
             "°C"),

    /**
     * Średnia temperatura arytmetyczna
     */
    AVG_TEMP("Temperatura średnia",
             "Średnia arytmetyczna wszystkich pomiarów",
             "°C"),

    /**
     * Mean Kinetic Temperature (ważna średnia)
     */
    MKT("MKT (Mean Kinetic Temperature)",
        "Ważna średnia temperatur; wzór: MKT = ΔH/R × (1/T_avg - 1/T_ref)",
        "°C"),

    /**
     * Odchylenie standardowe
     */
    STD_DEV("Odchylenie standardowe",
            "Wariancja rozkładu temperatur; σ = √(Σ(T_i - T_avg)² / (n-1))",
            "°C"),

    /**
     * Wspólczynnik trendu (drift)
     */
    DRIFT_COEFFICIENT("Współczynnik trendu",
                      "Liniowy drift temperatury podczas serii; R² trend line",
                      "-"),

    /**
     * Procent pomiarów w granicach (compliance percentage)
     */
    COMPLIANCE_PCT("Procent zgodności",
                   "Procent pomiarów w akceptowalnym przedziale temperatur",
                   "%"),

    /**
     * Maksymalny czas ciągłego naruszenia
     */
    MAX_VIOLATION_DURATION("Maks. czas naruszenia",
                           "Najdłuższy ciągły okres poza limitami",
                           "min"),

    /**
     * Całkowita liczba naruszenia (excursions)
     */
    TOTAL_VIOLATIONS("Łączne naruszenia",
                     "Całkowita liczba oddzielnych episodów naruszenia limitów",
                     "szt");

    private final String displayName;
    private final String description;
    private final String unit;

    AcceptanceCriterionField(String displayName, String description, String unit) {
        this.displayName = displayName;
        this.description = description;
        this.unit = unit;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getUnit() {
        return unit;
    }

    /**
     * Zwraca pełne wyświetlanie pola z jednostką
     */
    public String getFullDisplay() {
        return displayName + " (" + unit + ")";
    }

    /**
     * Sprawdza czy to pole jest domyślnie temperaturowe
     */
    public boolean isTemperatureField() {
        return this == MIN_TEMP || this == MAX_TEMP || this == AVG_TEMP || this == MKT;
    }

    /**
     * Zwraca emoji dla wizualnej identyfikacji
     */
    public String getIcon() {
        switch (this) {
            case MIN_TEMP:
            case MAX_TEMP:
            case AVG_TEMP:
            case MKT:
                return "🌡️";
            case STD_DEV:
            case DRIFT_COEFFICIENT:
                return "📈";
            case COMPLIANCE_PCT:
                return "✅";
            case MAX_VIOLATION_DURATION:
            case TOTAL_VIOLATIONS:
                return "⚠️";
            default:
                return "❓";
        }
    }
}
