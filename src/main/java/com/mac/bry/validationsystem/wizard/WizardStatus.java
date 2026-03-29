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
    ABANDONED("Porzucony", "Wizard anulowany przez użytkownika"),

    /**
     * Plan walidacji oczekuje na zatwierdzenie QA (PERIODIC_REVALIDATION only).
     *
     * <p>
     * Technik podpisał plan (step 8), wizard jest zablokowany na step >= 9.
     * QA musi zatwierdzić plan przed kontynuacją fazy pomiarowej.
     * </p>
     */
    AWAITING_QA_APPROVAL("Oczekuje na zatwierdzenie QA",
                         "Plan walidacji podpisany przez technologa, oczekuje na zatwierdzenie QA");

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

    /**
     * Sprawdza czy wizard jest w stanie oczekiwania na zatwierdzenie QA
     */
    public boolean isAwaitingQaApproval() {
        return this == AWAITING_QA_APPROVAL;
    }

    /**
     * Sprawdza czy wizard nie jest zakończony ani porzucony
     * (aktywny lub oczekujący na QA)
     */
    public boolean isInProgress() {
        return this == IN_PROGRESS || this == AWAITING_QA_APPROVAL;
    }
}
