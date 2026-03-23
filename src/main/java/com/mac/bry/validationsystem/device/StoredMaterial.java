package com.mac.bry.validationsystem.device;

public enum StoredMaterial {
    REAGENTS("Odczynniki"),
    SAMPLES("Próby");

    private final String displayName;

    StoredMaterial(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
