package com.mac.bry.validationsystem.thermorecorder;

public enum RecorderStatus {
    ACTIVE("Aktywny"),
    INACTIVE("Nieaktywny"),
    UNDER_CALIBRATION("Wysłano do wzorcowania");

    private final String displayName;

    RecorderStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
