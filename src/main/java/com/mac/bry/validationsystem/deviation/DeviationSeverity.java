package com.mac.bry.validationsystem.deviation;

/**
 * Klasyfikacja ciężkości naruszenia temperaturowego.
 * Progi oparte na wytycznych WHO PQS E006/TR06, CDC Vaccine Storage, EU GDP
 * Annex 5.
 */
public enum DeviationSeverity {
    CRITICAL("Krytyczne"),
    MAJOR("Większe"),
    MINOR("Mniejsze");

    private final String displayName;

    DeviationSeverity(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Klasyfikuje ciężkość na podstawie czasu trwania i odchylenia temperatury.
     *
     * @param durationMinutes czas trwania naruszenia [minuty]
     * @param deltaTemp       różnica między temperaturą szczytową a granicą [°C],
     *                        wartość bezwzględna
     * @return klasyfikacja severity
     */
    public static DeviationSeverity classify(long durationMinutes, double deltaTemp) {
        // CRITICAL: ≥ 60 min LUB ΔT ≥ 5°C (WHO PQS, CDC)
        if (durationMinutes >= 60 || deltaTemp >= 5.0) {
            return CRITICAL;
        }
        // MAJOR: 30–59 min LUB ΔT 2–5°C (CDC: cumulative ≥30min/24h = reportable)
        if (durationMinutes >= 30 || deltaTemp >= 2.0) {
            return MAJOR;
        }
        // MINOR: < 30 min I ΔT < 2°C (EU GDP Annex 5)
        return MINOR;
    }
}
