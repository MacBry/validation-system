package com.mac.bry.validationsystem.deviation;

/**
 * Typ naruszenia granicy temperaturowej.
 */
public enum ViolationType {
    ABOVE_UPPER("Powyżej górnej granicy"),
    BELOW_LOWER("Poniżej dolnej granicy");

    private final String displayName;

    ViolationType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
