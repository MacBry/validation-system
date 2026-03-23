package com.mac.bry.validationsystem.device;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO zawierające informacje o statusie walidacji urządzenia
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceValidationStatus {
    
    /**
     * ID urządzenia
     */
    private Long deviceId;
    
    /**
     * Czy urządzenie ma ważną walidację (nie starszą niż rok od ostatnich pomiarów)
     */
    private boolean hasValidValidation;
    
    /**
     * Data ostatnich pomiarów w seriach dla tego urządzenia
     */
    private LocalDateTime latestMeasurementDate;
    
    /**
     * Data ostatniej walidacji dla tego urządzenia
     */
    private LocalDateTime latestValidationDate;
    
    /**
     * Liczba dni od ostatniej walidacji
     */
    private Long daysSinceLastValidation;
    
    /**
     * Powód nieważności walidacji (jeśli hasValidValidation = false)
     * Możliwe wartości: "EXPIRED", "DRAFT", "REJECTED", "NO_VALIDATION"
     */
    private String invalidReason;
    
    /**
     * Czytelny opis przyczyny nieważności
     */
    public String getInvalidReasonDisplay() {
        if (invalidReason == null) return null;
        
        switch (invalidReason) {
            case "EXPIRED":
                return "Przedawniona (>365 dni)";
            case "DRAFT":
                return "Status: Projekt";
            case "REJECTED":
                return "Status: Odrzucona";
            case "COMPLETED":
                return "Zakończona";
            case "NO_VALIDATION":
                return "Brak walidacji";
            default:
                return "Nieznany";
        }
    }
}
