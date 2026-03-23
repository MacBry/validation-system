package com.mac.bry.validationsystem.validation;

/**
 * Status walidacji
 */
public enum ValidationStatus {
    DRAFT("Projekt"),
    APPROVED("Zatwierdzona"),
    REJECTED("Odrzucona"),
    COMPLETED("Zakończona");
    
    private final String displayName;
    
    ValidationStatus(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}
