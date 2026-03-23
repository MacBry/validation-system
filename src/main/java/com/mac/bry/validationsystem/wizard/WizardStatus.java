package com.mac.bry.validationsystem.wizard;

/**
 * Status szkicu walidacji w wizardzie.
 *
 * <p>
 * Reprezentuje stan trwającego procesu tworzenia walidacji.
 * </p>
 */
public enum WizardStatus {

    /**
     * Wizard jest w trakcie wypełniania (kroki 1-9)
     */
    IN_PROGRESS("W trakcie", "Wizard jest aktywnie wypełniany"),

    /**
     * Wizard został ukończony (krok 9 finalizacja + podpis)
     */
    COMPLETED("Ukończony", "Wizard finalizowany, walidacja utworzona"),

    /**
     * Wizard został porzucony (użytkownik anulował)
     */
    ABANDONED("Porzucony", "Wizard anulowany przez użytkownika");

    private final String displayName;
    private final String description;

    WizardStatus(String displayName, String description) {
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
     * Sprawdza czy status jest aktywny (można kontynuować)
     */
    public boolean isActive() {
        return this == IN_PROGRESS;
    }
}
