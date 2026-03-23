package com.mac.bry.validationsystem.measurement;

/**
 * Typ budżetu niepewności określający kontekst obliczenia
 */
public enum UncertaintyBudgetType {

    /**
     * Budżet niepewności dla pojedynczej serii pomiarowej (jeden rejestrator)
     */
    SERIES("Seria pomiarowa"),

    /**
     * Budżet niepewności dla całej walidacji (agregacja z wielu serii)
     */
    VALIDATION("Walidacja");

    private final String description;

    UncertaintyBudgetType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return description;
    }
}