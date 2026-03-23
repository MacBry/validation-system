package com.mac.bry.validationsystem.measurement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Wynik operacji upload plików .vi2
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadResult {
    
    /**
     * Lista pomyślnie przesłanych serii pomiarowych
     */
    @Builder.Default
    private List<MeasurementSeries> uploadedSeries = new ArrayList<>();
    
    /**
     * Lista ostrzeżeń (np. o temperaturach poza zakresem)
     */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();
    
    /**
     * Lista błędów (poważnych problemów uniemożliwiających upload)
     */
    @Builder.Default
    private List<String> errors = new ArrayList<>();
    
    /**
     * Czy upload zakończył się sukcesem (przynajmniej 1 plik przesłany)
     */
    public boolean isSuccess() {
        return !uploadedSeries.isEmpty();
    }
    
    /**
     * Czy są jakieś ostrzeżenia
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
    
    /**
     * Czy są jakieś błędy
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
